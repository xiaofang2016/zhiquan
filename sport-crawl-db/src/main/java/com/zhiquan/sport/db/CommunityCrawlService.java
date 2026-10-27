package com.zhiquan.sport.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class CommunityCrawlService {

    private static final TypeReference<Map<String, String>> STAMPS = new TypeReference<>() {
    };

    private final CommunityProperties properties;
    private final SportAwsProperties aws;
    private final CommunityClient client;
    private final S3DatabaseStore objects;
    private final ObjectMapper json;
    private final ReentrantLock lock = new ReentrantLock();

    public CommunityCrawlService(CommunityProperties properties,
                                 SportAwsProperties aws,
                                 CommunityClient client,
                                 S3DatabaseStore objects,
                                 ObjectMapper json) {
        this.properties = properties;
        this.aws = aws;
        this.client = client;
        this.objects = objects;
        this.json = json;
    }

    public void round() {
        if (!lock.tryLock()) {
            log.info("社球帝抓取正在进行，跳过这一轮");
            return;
        }
        try {
            List<JsonNode> incoming = new ArrayList<>();
            int pages = Math.max(1, properties.getPages());
            int pageSize = Math.max(1, properties.getPageSize());
            for (int page = 1; page <= pages; page++) {
                JsonNode list = client.get("/post/list?pageNum=" + page + "&pageSize=" + pageSize)
                        .path("data").path("list");
                if (!list.isArray() || list.isEmpty()) {
                    break;
                }
                list.forEach(incoming::add);
                if (list.size() < pageSize) {
                    break;
                }
            }
            if (incoming.isEmpty()) {
                log.info("社球帝这一轮没有帖子，保留上一份");
                return;
            }
            JsonNode circleList = client.get("/circle/list?pageNum=1&pageSize=50").path("data");
            Map<String, String> stamps = readStamps();
            List<Map<String, Object>> posts = new ArrayList<>();
            Set<Long> seenCircles = new LinkedHashSet<>();
            for (JsonNode node : incoming) {
                if (!CommunityCards.visible(node)) {
                    continue;
                }
                posts.add(CommunityCards.post(node));
                for (JsonNode circle : node.path("circles")) {
                    if (circle.path("circleId").canConvertToLong()) {
                        seenCircles.add(circle.path("circleId").asLong());
                    }
                }
                refreshComments(node, stamps);
            }
            Map<String, Object> feed = new LinkedHashMap<>();
            feed.put("circles", circlesInFeed(circleList, seenCircles));
            feed.put("posts", posts);
            objects.put(key("feed.json"), json.writeValueAsBytes(feed));
            objects.put(key("stamps.json"), json.writeValueAsBytes(stamps));
            log.info("社球帝写入 {} 帖", posts.size());
        } catch (Exception ex) {
            log.warn("社球帝抓取失败: {}", ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void refreshComments(JsonNode post, Map<String, String> stamps) {
        String id = post.path("postId").asText();
        if (!id.matches("[0-9]{1,18}")) {
            return;
        }
        String stamp = CommunityCards.stamp(post);
        if (stamp.equals(stamps.get(id))) {
            return;
        }
        try {
            List<Map<String, Object>> comments = new ArrayList<>();
            if (post.path("commentCount").asInt(0) > 0) {
                int size = Math.max(1, properties.getCommentPageSize());
                JsonNode list = client.get("/comment/list?targetType=post&targetId=" + id
                        + "&sortRule=newest&pageNum=1&pageSize=" + size + "&userType=live")
                        .path("data").path("list");
                if (list.isArray()) {
                    for (JsonNode comment : list) {
                        if (comment.path("commentId").canConvertToLong()) {
                            comments.add(CommunityCards.comment(comment));
                        }
                    }
                }
            }
            objects.put(key("posts/" + id + "/comments.json"), json.writeValueAsBytes(Map.of("comments", comments)));
            stamps.put(id, stamp);
        } catch (Exception ex) {
            log.warn("社球帝评论 {} 没有写入: {}", id, ex.getMessage());
        }
    }

    private List<Map<String, Object>> circlesInFeed(JsonNode circleList, Set<Long> seen) {
        List<Map<String, Object>> circles = new ArrayList<>();
        if (!circleList.isArray()) {
            return circles;
        }
        for (JsonNode node : circleList) {
            if (!node.path("circleId").canConvertToLong() || !seen.contains(node.path("circleId").asLong())) {
                continue;
            }
            circles.add(CommunityCards.circle(node));
        }
        return circles;
    }

    private Map<String, String> readStamps() {
        byte[] body = objects.get(key("stamps.json"));
        if (body == null || body.length == 0) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> stamps = json.readValue(body, STAMPS);
            return stamps == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stamps);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String key(String name) {
        String prefix = aws.getCommunityPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "community/";
        }
        return prefix.endsWith("/") ? prefix + name : prefix + "/" + name;
    }
}
