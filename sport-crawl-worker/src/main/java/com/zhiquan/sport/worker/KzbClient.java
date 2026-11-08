package com.zhiquan.sport.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Component
public class KzbClient {

    static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final CrawlProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Object paceLock = new Object();
    private long lastRequestAt;

    public KzbClient(CrawlProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restTemplate = new RestTemplate(factory);
    }

    public JsonNode categoryList() {
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl() + "/prod-api/category/list")
                .queryParam("type", 0)
                .queryParam("isoften", 1)
                .queryParam("ishot", 1)
                .queryParam("langtype", "zh")
                .build()
                .encode()
                .toUri();
        return getJson(uri);
    }

    public JsonNode dateCount() {
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl() + "/prod-api/match/date/count")
                .queryParam("type", -1)
                .queryParam("cid", 0)
                .build()
                .encode()
                .toUri();
        return getJson(uri);
    }

    public JsonNode matchList(LocalDate date, int page, int pageSize) {
        return matchList(date, page, pageSize, properties.getZhuboType());
    }

    public JsonNode matchList(LocalDate date, int page, int pageSize, int zhuboType) {
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl() + "/prod-api/match/list/new")
                .queryParam("isfanye", 1)
                .queryParam("type", 0)
                .queryParam("cid", 0)
                .queryParam("ishot", -1)
                .queryParam("pn", page)
                .queryParam("ps", pageSize)
                .queryParam("langtype", "zh")
                .queryParam("starttime", date.toString())
                .queryParam("pid", 4)
                .queryParam("zoneId", properties.getZoneId())
                .queryParam("zhuboType", zhuboType)
                .build()
                .encode()
                .toUri();
        return getJson(uri);
    }

    public Downloaded getBytes(URI uri, int maxBytes) {
        pace();
        return restTemplate.execute(uri, HttpMethod.GET, request -> {
            request.getHeaders().set(HttpHeaders.USER_AGENT, USER_AGENT);
            request.getHeaders().set(HttpHeaders.REFERER, "https://kzb001.net/");
            request.getHeaders().setAccept(List.of(MediaType.ALL));
        }, response -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("HTTP " + response.getStatusCode().value());
            }
            byte[] body = response.getBody().readNBytes(maxBytes + 1);
            if (body.length > maxBytes) {
                throw new IllegalStateException("image larger than " + maxBytes);
            }
            String contentType = response.getHeaders().getContentType() == null
                    ? ""
                    : response.getHeaders().getContentType().toString();
            return new Downloaded(body, contentType);
        });
    }

    private JsonNode getJson(URI uri) {
        pace();
        String body = restTemplate.execute(uri, HttpMethod.GET, request -> {
            request.getHeaders().set(HttpHeaders.USER_AGENT, USER_AGENT);
            request.getHeaders().set(HttpHeaders.REFERER, "https://kzb001.net/");
            request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
        }, response -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("HTTP " + response.getStatusCode().value());
            }
            return new String(response.getBody().readAllBytes());
        });
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"0".equals(root.path("code").asText())) {
                throw new IllegalStateException("upstream " + root.path("code").asText()
                        + " " + root.path("msg").asText());
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("upstream json", ex);
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
                    throw new IllegalStateException("crawl interrupted", ex);
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }

    public record Downloaded(byte[] body, String contentType) {
    }
}
