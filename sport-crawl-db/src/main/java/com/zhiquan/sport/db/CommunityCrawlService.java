package com.zhiquan.sport.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
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
            List<Map<String, Object>> posts = new ArrayList<>();
            for (JsonNode node : unique.values()) {
                Map<String, Object> card = CommunityCards.post(node, properties.getBaseUrl());
                card.put("head", heads.place(String.valueOf(card.get("head"))));
                fillDuration(card, durations);
                String id = node.path("postId").asText();
                if (counts.containsKey(id)) {
                    card.put("commentCount", counts.get(id));
                }
                posts.add(card);
                refreshPost(node, stamps, card, counts);
            }
            Map<String, Object> feed = new LinkedHashMap<>();
            feed.put("circles", circles);
            feed.put("posts", posts);
            objects.put(key("feed.json"), json.writeValueAsBytes(feed));
            objects.put(key("stamps.json"), json.writeValueAsBytes(stamps));
            objects.put(key("video-durations.json"), json.writeValueAsBytes(durations));
            objects.put(key("comment-counts.json"), json.writeValueAsBytes(counts));
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

    private void refreshPost(JsonNode post, Map<String, String> stamps, Map<String, Object> card, Map<String, Integer> counts) {
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
                Map<String, Object> mapped = CommunityCards.comment(comment);
                if (mapped != null) {
                    placeHeads(mapped);
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
