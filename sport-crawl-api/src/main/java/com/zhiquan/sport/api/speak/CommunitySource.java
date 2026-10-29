package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.util.ArrayList;
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
            List<Map<String, Object>> comments = comments(postId);
            card.put("commentCount", CommunityCards.shown(comments));
            objects.put(key("posts/" + postId + ".json"), json.writeValueAsBytes(card));
            objects.put(key("posts/" + postId + "/comments.json"), json.writeValueAsBytes(Map.of("comments", comments)));
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
                Map<String, Object> mapped = CommunityCards.comment(comment);
                if (mapped == null) {
                    continue;
                }
                mapped.put("head", heads.place(String.valueOf(mapped.get("head"))));
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

    private String key(String name) {
        String prefix = aws.getCommunityPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "community/";
        }
        return prefix.endsWith("/") ? prefix + name : prefix + "/" + name;
    }
}
