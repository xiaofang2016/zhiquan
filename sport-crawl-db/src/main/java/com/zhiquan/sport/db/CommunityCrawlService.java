package com.zhiquan.sport.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.community.CommunityCards;
import com.zhiquan.sport.store.community.CommunityHeads;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import com.zhiquan.sport.store.repo.S3ImageStore;
import com.zhiquan.sport.store.repo.SportImageRepository;
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
    private final CommunityHeads heads;
    private final ReentrantLock lock = new ReentrantLock();

    public CommunityCrawlService(CommunityProperties properties,
                                 SportAwsProperties aws,
                                 CommunityClient client,
                                 S3DatabaseStore objects,
                                 ObjectMapper json,
                                 SportImageRepository images,
                                 S3ImageStore imageStore) {
        this.properties = properties;
        this.aws = aws;
        this.client = client;
        this.objects = objects;
        this.json = json;
        this.heads = new CommunityHeads(images, imageStore, properties.getPublicBase());
    }

    public void round() {
        if (!lock.tryLock()) {
            log.info("社球帝抓取正在进行，跳过这一轮");
            return;
        }
        try {
            heads.beginRound();
            List<JsonNode> incoming = fetchPosts(null, null, properties.getPages());
            if (incoming.isEmpty()) {
                log.info("社球帝这一轮没有帖子，保留上一份");
                return;
            }
            try {
                incoming.addAll(fetchPosts(null, "1", properties.getEssencePages()));
            } catch (Exception ex) {
                log.warn("社球帝精华没有拉到: {}", ex.getMessage());
            }
            Map<String, Integer> durations = readDurations();
            Map<String, Integer> counts = readCounts();
            JsonNode circleList = client.get("/circle/list?pageNum=1&pageSize=100").path("data");
            List<Map<String, Object>> circles = new ArrayList<>();
            if (circleList.isArray()) {
                for (JsonNode node : circleList) {
                    if (!node.path("circleId").canConvertToLong()) {
                        continue;
                    }
                    long circleId = node.path("circleId").asLong();
                    JsonNode detail = node;
                    try {
                        JsonNode loaded = client.get("/circle/detail?circleId=" + circleId).path("data");
                        if (loaded.path("circleId").canConvertToLong()) {
                            detail = loaded;
                        }
                    } catch (Exception ex) {
                        log.warn("社球帝圈子 {} 详情没有拉到: {}", circleId, ex.getMessage());
                    }
                    if (detail instanceof ObjectNode object) {
                        mergeTopicAnnounces(object);
                    }
                    Map<String, Object> profile = CommunityCards.circle(detail);
                    profile.put("ownerHead", heads.place(String.valueOf(profile.get("ownerHead"))));
                    circles.add(profile);
                    try {
                        incoming.addAll(fetchPosts(circleId, null, properties.getCirclePages()));
                    } catch (Exception ex) {
                        log.warn("社球帝圈子 {} 帖子没有拉到: {}", circleId, ex.getMessage());
                    }
                }
            }
            Map<Long, JsonNode> unique = new LinkedHashMap<>();
            for (JsonNode node : incoming) {
                if (CommunityCards.visible(node)) {
                    unique.putIfAbsent(node.path("postId").asLong(), node);
                }
            }
            Map<String, String> stamps = readStamps();
            Map<String, String> commentIndex = readIndex();
            Map<String, java.util.List<String>> commentUpdates = new LinkedHashMap<>();
            List<Map<String, Object>> posts = new ArrayList<>();
            for (JsonNode node : unique.values()) {
                Map<String, Object> card = CommunityCards.post(node, properties.getBaseUrl());
                card.put("head", heads.place(String.valueOf(card.get("head"))));
                placeHotHead(card);
                fillDuration(card, durations);
                String id = node.path("postId").asText();
                if (counts.containsKey(id)) {
                    card.put("commentCount", counts.get(id));
                }
                posts.add(card);
                refreshPost(node, stamps, card, counts, commentUpdates);
            }
            Map<String, Object> feed = new LinkedHashMap<>();
            feed.put("circles", circles);
            feed.put("posts", posts);
            objects.put(key("feed.json"), json.writeValueAsBytes(feed));
            objects.put(key("stamps.json"), json.writeValueAsBytes(stamps));
            objects.put(key("video-durations.json"), json.writeValueAsBytes(durations));
            applyIndex(commentIndex, commentUpdates);
            objects.put(key("comment-counts.json"), json.writeValueAsBytes(counts));
            objects.put(key("comment-index.json"), json.writeValueAsBytes(commentIndex));
            log.info("社球帝写入 {} 帖", posts.size());
        } catch (Exception ex) {
            log.warn("社球帝抓取失败: {}", ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private List<JsonNode> fetchPosts(Long circleId, String essence, int pages) {
        List<JsonNode> incoming = new ArrayList<>();
        int pageSize = Math.max(1, properties.getPageSize());
        int limit = Math.max(1, pages);
        for (int page = 1; page <= limit; page++) {
            StringBuilder path = new StringBuilder("/post/list?pageNum=")
                    .append(page).append("&pageSize=").append(pageSize);
            if (circleId != null) {
                path.append("&circleId=").append(circleId);
            }
            if (essence != null && !essence.isBlank()) {
                path.append("&isEssence=").append(essence);
            }
            JsonNode list = client.get(path.toString()).path("data").path("list");
            if (!list.isArray() || list.isEmpty()) {
                break;
            }
            list.forEach(incoming::add);
            if (list.size() < pageSize) {
                break;
            }
        }
        return incoming;
    }

    private void refreshPost(JsonNode post, Map<String, String> stamps, Map<String, Object> card, Map<String, Integer> counts, Map<String, java.util.List<String>> commentUpdates) {
        String id = post.path("postId").asText();
        if (!id.matches("[0-9]{1,18}")) {
            return;
        }
        String stamp = CommunityCards.stamp(post);
        if (stamp.equals(stamps.get(id))) {
            return;
        }
        try {
            List<Map<String, Object>> comments = post.path("commentCount").asInt(0) > 0 ? loadComments(id) : List.of();
            java.util.List<String> ids = new ArrayList<>();
            for (Map<String, Object> comment : comments) {
                Object commentId = comment.get("commentId");
                if (commentId != null) {
                    ids.add(String.valueOf(commentId));
                }
            }
            commentUpdates.put(id, ids);
            int shown = CommunityCards.shown(comments);
            card.put("commentCount", shown);
            counts.put(id, shown);
            objects.put(key("posts/" + id + ".json"), json.writeValueAsBytes(card));
            objects.put(key("posts/" + id + "/comments.json"), json.writeValueAsBytes(Map.of("comments", comments)));
            stamps.put(id, stamp);
        } catch (Exception ex) {
            log.warn("社球帝帖子 {} 没有写入: {}", id, ex.getMessage());
        }
    }

    private List<Map<String, Object>> loadComments(String id) {
        List<Map<String, Object>> comments = new ArrayList<>();
        int size = Math.max(1, properties.getCommentPageSize());
        int maxPages = Math.max(1, properties.getCommentPages());
        for (int page = 1; page <= maxPages; page++) {
            JsonNode data = client.get("/comment/list?targetType=post&targetId=" + id
                    + "&sortRule=newest&pageNum=" + page + "&pageSize=" + size + "&userType=live").path("data");
            JsonNode list = data.path("list");
            if (!list.isArray() || list.isEmpty()) {
                break;
            }
            for (JsonNode comment : list) {
                Map<String, Object> mapped = CommunityCards.comment(comment, properties.getBaseUrl());
                if (mapped != null) {
                    placeHeads(mapped);
                    fillReplies(mapped);
                    comments.add(mapped);
                }
            }
            if (page >= data.path("pages").asInt(1)) {
                break;
            }
        }
        return comments;
    }

    @SuppressWarnings("unchecked")
    private void placeHeads(Map<String, Object> person) {
        person.put("head", heads.place(String.valueOf(person.get("head"))));
        Object replies = person.get("replies");
        if (replies instanceof List<?> list) {
            for (Object reply : list) {
                if (reply instanceof Map<?, ?> map) {
                    ((Map<String, Object>) map).put("head", heads.place(String.valueOf(map.get("head"))));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void fillDuration(Map<String, Object> card, Map<String, Integer> known) {
        Object videos = card.get("videos");
        if (!(videos instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> video = (Map<String, Object>) raw;
            if (video.get("duration") instanceof Integer) {
                continue;
            }
            String url = String.valueOf(video.get("url"));
            if (url.isEmpty() || "null".equals(url)) {
                continue;
            }
            Integer saved = known.get(url);
            if (saved != null) {
                video.put("duration", saved);
                continue;
            }
            try {
                String playlist = client.text(url);
                String variant = CommunityDuration.variant(playlist);
                if (variant != null) {
                    playlist = client.text(resolve(url, variant));
                }
                Integer seconds = CommunityDuration.seconds(playlist);
                if (seconds != null) {
                    video.put("duration", seconds);
                    known.put(url, seconds);
                }
            } catch (Exception ex) {
                log.warn("社球帝视频时长没有拿到: {}", ex.getMessage());
            }
        }
    }

    private static String resolve(String playlistUrl, String variant) {
        if (variant.startsWith("http://") || variant.startsWith("https://")) {
            return variant;
        }
        int cut = playlistUrl.lastIndexOf('/');
        if (cut < 0) {
            return variant;
        }
        return playlistUrl.substring(0, cut + 1) + variant;
    }

    private Map<String, Integer> readCounts() {
        return readIntegers("comment-counts.json");
    }

    private Map<String, Integer> readDurations() {
        return readIntegers("video-durations.json");
    }

    private Map<String, Integer> readIntegers(String name) {
        byte[] body = objects.get(key(name));
        if (body == null || body.length == 0) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Integer> saved = json.readValue(body, new TypeReference<Map<String, Integer>>() {
            });
            return saved == null ? new LinkedHashMap<>() : new LinkedHashMap<>(saved);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    /** 公告正文在圈子详情或话题详情的 announces 里，列表接口没有。 */
    private void mergeTopicAnnounces(ObjectNode circle) {
        ArrayNode announces = circle.path("announces") instanceof ArrayNode existing
                ? existing
                : circle.putArray("announces");
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : announces) {
            String id = item.path("announceId").asText(item.path("id").asText(""));
            if (!id.isEmpty()) {
                seen.add(id);
            }
        }
        for (JsonNode topic : circle.path("topics")) {
            if (!topic.path("topicId").canConvertToLong()) {
                continue;
            }
            long topicId = topic.path("topicId").asLong();
            try {
                JsonNode extra = client.get("/topic/detail?topicId=" + topicId).path("data").path("announces");
                if (!extra.isArray()) {
                    continue;
                }
                for (JsonNode item : extra) {
                    String id = item.path("announceId").asText(item.path("id").asText(""));
                    if (!id.isEmpty() && !seen.add(id)) {
                        continue;
                    }
                    announces.add(item);
                }
            } catch (Exception ex) {
                log.warn("社球帝话题 {} 的公告没有拉到: {}", topicId, ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void fillReplies(Map<String, Object> comment) {
        Object raw = comment.get("replies");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        int count = comment.get("replyCount") instanceof Number number ? number.intValue() : list.size();
        if (count <= list.size()) {
            return;
        }
        List<Map<String, Object>> replies = (List<Map<String, Object>>) list;
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Map<String, Object> reply : replies) {
            if (reply.get("replyId") != null) {
                seen.add(String.valueOf(reply.get("replyId")));
            }
        }
        String id = String.valueOf(comment.get("commentId"));
        try {
            for (int page = 1; page <= 5; page++) {
                JsonNode data = client.get("/comment/reply/list?commentId=" + id
                        + "&sortRule=newest&pageNum=" + page + "&pageSize=50").path("data");
                JsonNode rows = data.path("list");
                if (!rows.isArray() || rows.isEmpty()) {
                    break;
                }
                for (JsonNode row : rows) {
                    Map<String, Object> mapped = CommunityCards.reply(row, properties.getBaseUrl());
                    if (mapped == null || !seen.add(String.valueOf(mapped.get("replyId")))) {
                        continue;
                    }
                    mapped.put("head", heads.place(String.valueOf(mapped.get("head"))));
                    replies.add(mapped);
                }
                if (page >= data.path("pages").asInt(1)) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("社球帝评论 {} 的回复没有补全: {}", id, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void placeHotHead(Map<String, Object> card) {
        Object hot = card.get("hotComment");
        if (hot instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put("head", heads.place(String.valueOf(map.get("head"))));
        }
    }

    private void applyIndex(Map<String, String> index, Map<String, java.util.List<String>> updates) {
        for (Map.Entry<String, java.util.List<String>> update : updates.entrySet()) {
            index.entrySet().removeIf(entry -> update.getKey().equals(entry.getValue()));
            for (String commentId : update.getValue()) {
                index.put(commentId, update.getKey());
            }
        }
    }

    private Map<String, String> readIndex() {
        byte[] body = objects.get(key("comment-index.json"));
        if (body == null || body.length == 0) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> saved = json.readValue(body, STAMPS);
            return saved == null ? new LinkedHashMap<>() : new LinkedHashMap<>(saved);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
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
