package com.zhiquan.sport.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.db.DatabaseQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class UpstreamClient {

    static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final DbProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Object paceLock = new Object();
    private long lastRequestAt;

    public UpstreamClient(DbProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restTemplate = new RestTemplate(factory);
    }

    public byte[] get(String path, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl() + "/prod-api" + path);
        DatabaseQuery.normalize(query).forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();
        pace();
        byte[] body;
        try {
            body = restTemplate.execute(uri, HttpMethod.GET, request -> {
                request.getHeaders().set(HttpHeaders.USER_AGENT, USER_AGENT);
                request.getHeaders().set(HttpHeaders.REFERER, "https://kzb001.net/");
                request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
            }, response -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new IllegalStateException("HTTP " + response.getStatusCode().value());
                }
                return response.getBody().readAllBytes();
            });
        } catch (HttpStatusCodeException ex) {
            throw new IllegalStateException("HTTP " + ex.getStatusCode().value());
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"0".equals(root.path("code").asText())) {
                String detail = root.path("msg").asText("");
                String info = root.path("info").asText("");
                if (!info.isBlank()) {
                    detail = detail.isBlank() ? info : detail + " " + info;
                }
                throw new IllegalStateException("upstream " + root.path("code").asText() + " " + detail);
            }
            return body;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("upstream json " + new String(body, StandardCharsets.UTF_8).length(), ex);
        }
    }

    private void pace() {
        synchronized (paceLock) {
            long wait = properties.getRequestIntervalMs() - (System.currentTimeMillis() - lastRequestAt);
            if (lastRequestAt != 0 && wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("database crawl interrupted", ex);
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }
}
