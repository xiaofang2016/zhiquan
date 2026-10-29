package com.zhiquan.sport.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class CommunityClient {

    private final CommunityProperties properties;
    private final ObjectMapper json;
    private final RestTemplate restTemplate;
    private final Object paceLock = new Object();
    private long lastRequestAt;

    public CommunityClient(CommunityProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.restTemplate = new RestTemplate(factory);
    }

    public JsonNode get(String pathAndQuery) {
        URI uri = URI.create(properties.getBaseUrl() + "/comment-api" + pathAndQuery);
        pace();
        byte[] body = restTemplate.execute(uri, HttpMethod.GET, request -> {
            request.getHeaders().set(HttpHeaders.USER_AGENT, UpstreamClient.USER_AGENT);
            request.getHeaders().set(HttpHeaders.REFERER, "https://kzb001.net/community/");
            request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
        }, response -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("HTTP " + response.getStatusCode().value());
            }
            return response.getBody().readAllBytes();
        });
        try {
            JsonNode root = json.readTree(body);
            if (root.path("code").asInt(-1) != 0) {
                throw new IllegalStateException("社球帝 " + root.path("code").asText() + " " + root.path("msg").asText());
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("社球帝响应无法解析", ex);
        }
    }

    public String text(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "";
        }
        pace();
        byte[] body = restTemplate.execute(uri, HttpMethod.GET, request -> {
            request.getHeaders().set(HttpHeaders.USER_AGENT, UpstreamClient.USER_AGENT);
            request.getHeaders().set(HttpHeaders.REFERER, "https://kzb001.net/community/");
        }, response -> {
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return new byte[0];
            }
            byte[] bytes = response.getBody().readNBytes(256 * 1024);
            return bytes == null ? new byte[0] : bytes;
        });
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private void pace() {
        synchronized (paceLock) {
            long wait = properties.getRequestIntervalMs() - (System.currentTimeMillis() - lastRequestAt);
            if (lastRequestAt != 0 && wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("社球帝抓取被打断", ex);
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }
}
