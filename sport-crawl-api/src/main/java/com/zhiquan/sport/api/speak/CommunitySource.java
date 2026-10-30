package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.community.CommunityCards;
import com.zhiquan.sport.store.community.CommunityHeads;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import com.zhiquan.sport.store.repo.S3ImageStore;
import com.zhiquan.sport.store.repo.SportImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 打开一篇还没进抓取窗口的帖子时，向上游要这一篇和它的评论。 */
@Service
public class CommunitySource {

    private static final Logger log = LoggerFactory.getLogger(CommunitySource.class);
    private static final String AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final S3DatabaseStore objects;
    private final SportAwsProperties aws;
    private final ObjectMapper json;
    private final CommunityHeads heads;
    private final String baseUrl;
    private final RestTemplate http;

    public CommunitySource(S3DatabaseStore objects,
                           SportAwsProperties aws,
                           ObjectMapper json,
                           SportImageRepository images,
                           S3ImageStore imageStore,
                           @Value("${sport.community.base-url:https://kzb001.net}") String baseUrl,
                           @Value("${sport.community.public-base:https://dgtplaoir00la.cloudfront.net}") String publicBase) {
        this.objects = objects;
        this.aws = aws;
        this.json = json;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.heads = new CommunityHeads(images, imageStore, publicBase);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.http = new RestTemplate(factory);
    }

    public void fill(String postId) {
        if (postId == null || !postId.matches("[0-9]{1,18}")) {
            return;
        }
        try {
            JsonNode detail = get("/post/detail?postId=" + postId).path("data");
            if (!CommunityCards.visible(detail)) {
                return;
            }
            Map<String, Object> card = CommunityCards.post(detail, baseUrl);
            card.put("head", heads.place(String.valueOf(card.get("head"))));
            placeHotHead(card);
            List<Map<String, Object>> comments = comments(postId);
            card.put("commentCount", CommunityCards.shown(comments));
            objects.put(key("posts/" + postId + ".json"), json.writeValueAsBytes(card));
            objects.put(key("posts/" + postId + "/comments.json"), json.writeValueAsBytes(Map.of("comments", comments)));
            remember(postId, comments);
        } catch (Exception ex) {
            log.warn("社球帝帖子 {} 没有补到: {}", postId, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> comments(String postId) {
        List<Map<String, Object>> comments = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            JsonNode data = get("/comment/list?targetType=post&targetId=" + postId
                    + "&sortRule=newest&pageNum=" + page + "&pageSize=50&userType=live").path("data");
            JsonNode list = data.path("list");
            if (!list.isArray() || list.isEmpty()) {
                break;
            }
            for (JsonNode comment : list) {
                Map<String, Object> mapped = CommunityCards.comment(comment, baseUrl);
                if (mapped == null) {
                    continue;
                }
                mapped.put("head", heads.place(String.valueOf(mapped.get("head"))));
                appendMissingReplies(mapped);
                Object replies = mapped.get("replies");
                if (replies instanceof List<?> rows) {
                    for (Object row : rows) {
                        if (row instanceof Map<?, ?> map) {
                            ((Map<String, Object>) map).put("head", heads.place(String.valueOf(map.get("head"))));
                        }
                    }
                }
                comments.add(mapped);
            }
            if (page >= data.path("pages").asInt(1)) {
                break;
            }
        }
        return comments;
    }

    @SuppressWarnings("unchecked")
    private void appendMissingReplies(Map<String, Object> comment) {
        Object raw = comment.get("replies");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        int count = comment.get("replyCount") instanceof Number number ? number.intValue() : list.size();
        if (count <= list.size()) {
            return;
        }
        ArrayNode extra = moreReplies(String.valueOf(comment.get("commentId")));
        List<Map<String, Object>> replies = (List<Map<String, Object>>) list;
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Object item : replies) {
            if (item instanceof Map<?, ?> map && map.get("replyId") != null) {
                seen.add(String.valueOf(map.get("replyId")));
            }
        }
        for (JsonNode row : extra) {
            if (!row.path("replyId").canConvertToLong() || !seen.add(row.path("replyId").asText())) {
                continue;
            }
            replies.add(json.convertValue(row, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            }));
        }
    }

    /**
     * 原站嵌在评论里的回复不够时，剩下的在这条接口里。
     */
    public ArrayNode moreReplies(String commentId) {
        ArrayNode rows = json.createArrayNode();
        if (commentId == null || !commentId.matches("[0-9]{1,18}")) {
            return rows;
        }
        try {
            for (int page = 1; page <= 10; page++) {
                JsonNode data = get("/comment/reply/list?commentId=" + commentId
                        + "&sortRule=newest&pageNum=" + page + "&pageSize=50").path("data");
                JsonNode list = data.path("list");
                if (!list.isArray() || list.isEmpty()) {
                    break;
                }
                for (JsonNode reply : list) {
                    Map<String, Object> mapped = CommunityCards.reply(reply, baseUrl);
                    if (mapped == null) {
                        continue;
                    }
                    mapped.put("head", heads.place(String.valueOf(mapped.get("head"))));
                    rows.add(json.valueToTree(mapped));
                }
                if (page >= data.path("pages").asInt(1)) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("社球帝评论 {} 的回复没有补到: {}", commentId, ex.getMessage());
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private void placeHotHead(Map<String, Object> card) {
        Object hot = card.get("hotComment");
        if (hot instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put("head", heads.place(String.valueOf(map.get("head"))));
        }
    }

    private void remember(String postId, List<Map<String, Object>> comments) {
        Map<String, String> index = readIndex();
        index.entrySet().removeIf(entry -> postId.equals(entry.getValue()));
        for (Map<String, Object> comment : comments) {
            Object id = comment.get("commentId");
            if (id != null) {
                index.put(String.valueOf(id), postId);
            }
        }
        try {
            objects.put(key("comment-index.json"), json.writeValueAsBytes(index));
        } catch (Exception ex) {
            log.warn("社球帝评论索引没有写入: {}", ex.getMessage());
        }
    }

    private Map<String, String> readIndex() {
        byte[] body = objects.get(key("comment-index.json"));
        if (body == null || body.length == 0) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = json.readTree(body);
            Map<String, String> index = new LinkedHashMap<>();
            node.fields().forEachRemaining(field -> index.put(field.getKey(), field.getValue().asText("")));
            return index;
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    public String playlist(String taskId) {
        return new String(readBytes(baseUrl + "/comment-api/video/hls/m3u8?taskId=" + taskId), StandardCharsets.UTF_8);
    }

    public byte[] videoKey(String taskId) {
        return readBytes(baseUrl + "/comment-api/video/hls/key?taskId=" + taskId);
    }

    public byte[] videoFile(URI uri) {
        return readBytes(uri.toString());
    }

    private JsonNode get(String pathAndQuery) {
        URI uri = URI.create(baseUrl + "/comment-api" + pathAndQuery);
        try {
            byte[] body = http.execute(uri, HttpMethod.GET, request -> {
                request.getHeaders().set(HttpHeaders.USER_AGENT, AGENT);
                request.getHeaders().set(HttpHeaders.REFERER, "https://kzb001.net/community/");
                request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
            }, response -> response.getBody() == null ? new byte[0] : response.getBody().readAllBytes());
            if (body == null || body.length == 0) {
                return json.createObjectNode();
            }
            return json.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private byte[] readBytes(String url) {
        try {
            byte[] body = http.execute(URI.create(url), HttpMethod.GET, request -> {
                request.getHeaders().set(HttpHeaders.USER_AGENT, AGENT);
                request.getHeaders().set(HttpHeaders.REFERER, "https://kzb001.net/community/");
            }, response -> {
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    return new byte[0];
                }
                return response.getBody().readNBytes(8 * 1024 * 1024);
            });
            if (body == null || body.length == 0) {
                throw new IllegalArgumentException("视频暂时没有拿到");
            }
            return body;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("视频暂时没有拿到");
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
