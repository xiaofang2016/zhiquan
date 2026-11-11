package com.zhiquan.sport.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原站有、网关以前没有的只读接口。资料库不走这里。
 */
@RestController
public class UpstreamMirrorController {

    static final Set<String> PATHS = Set.of(
            "/prod-api/gift/getGiftList",
            "/prod-api/bobble/getBobbleList",
            "/prod-api/match/news/information/list",
            "/prod-api/match/news/information/related/list",
            "/prod-api/match/detail/tabs",
            "/prod-api/match/detail/lineupAnalysis",
            "/prod-api/match/detail/playersStatSeason",
            "/prod-api/match/detail/seasonData",
            "/prod-api/match/detail/player",
            "/prod-api/data/lanqiu/ranklist",
            "/prod-api/search/hot",
            "/prod-api/search/content",
            "/prod-api/get/search/history",
            "/prod-api/clear/search/history",
            "/prod-api/news/list",
            "/prod-api/news/detail",
            "/prod-api/news/information/detail",
            "/prod-api/at/list"
    );

    private static final long FRESH_MS = 60_000;
    private static final int BODY_LIMIT = 2_000_000;

    private final String baseUrl;
    private final RestTemplate http;
    private final SearchHistory searches;
    private final ObjectMapper jsonMapper;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public UpstreamMirrorController(@Value("${sport.community.base-url:https://kzb001.net}") String baseUrl,
                                    SearchHistory searches,
                                    ObjectMapper jsonMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.searches = searches;
        this.jsonMapper = jsonMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = new RestTemplate(factory);
    }

    @GetMapping({
            "/prod-api/gift/getGiftList",
            "/prod-api/bobble/getBobbleList",
            "/prod-api/match/news/information/list",
            "/prod-api/match/news/information/related/list",
            "/prod-api/match/detail/tabs",
            "/prod-api/match/detail/lineupAnalysis",
            "/prod-api/match/detail/playersStatSeason",
            "/prod-api/match/detail/seasonData",
            "/prod-api/match/detail/player",
            "/prod-api/data/lanqiu/ranklist",
            "/prod-api/search/hot",
            "/prod-api/search/content",
            "/prod-api/get/search/history",
            "/prod-api/clear/search/history",
            "/prod-api/news/list",
            "/prod-api/news/detail",
            "/prod-api/news/information/detail",
            "/prod-api/at/list"
    })
    public ResponseEntity<byte[]> mirror(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!PATHS.contains(path)) {
            return ResponseEntity.notFound().build();
        }
        String query = request.getQueryString();
        if (query != null && query.length() > 2000) {
            throw new IllegalArgumentException("参数过长");
        }
        if ("/prod-api/get/search/history".equals(path)) {
            return history(query, false);
        }
        if ("/prod-api/clear/search/history".equals(path)) {
            return history(query, true);
        }
        String upstream = "/prod-api/search/content".equals(path) ? SearchPages.withoutAccount(query) : query;
        Cached loaded = fetch(path, upstream);
        byte[] body = loaded.body();
        if (loaded.ok() && "/prod-api/search/content".equals(path) && SearchPages.matchFirstPage(upstream, body, jsonMapper)) {
            Cached next = fetch(path, SearchPages.withPage(upstream, 2));
            if (next.ok() && SearchPages.laterPageEmpty(next.body(), jsonMapper)) {
                body = SearchPages.clamp(body, jsonMapper);
                cache.put(cacheKey(path, upstream), new Cached(body, true, loaded.until()));
            }
        }
        if ("/prod-api/search/content".equals(path)) {
            searches.remember(query);
        }
        boolean shared = !"/prod-api/search/content".equals(path) || SearchPages.param(query, "token").isBlank();
        return json(body, loaded.ok(), shared);
    }

    @PostMapping("/prod-api/clear/search/history")
    public ResponseEntity<byte[]> clearHistory(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query != null && query.length() > 2000) {
            throw new IllegalArgumentException("参数过长");
        }
        return history(query, true);
    }

    private ResponseEntity<byte[]> history(String query, boolean clear) {
        Cached loaded = fetch("/prod-api/get/search/history", SearchPages.withoutAccount(query));
        String token = SearchPages.param(query, "token");
        if (clear) {
            searches.clear(token);
        }
        java.util.List<String> keys = clear ? java.util.List.of() : searches.keys(token);
        byte[] body = loaded.ok() ? searches.overlay(loaded.body(), keys) : searches.local(keys);
        return json(body, true, false);
    }

    private Cached fetch(String path, String query) {
        String key = cacheKey(path, query);
        Cached hit = cache.get(key);
        if (hit != null && hit.until > System.currentTimeMillis()) {
            return hit;
        }
        Cached loaded = load(key);
        if (loaded.ok) {
            cache.put(key, loaded);
        }
        return loaded;
    }

    private static String cacheKey(String path, String query) {
        return query == null || query.isBlank() ? path : path + "?" + query;
    }

    private Cached load(String key) {
        URI uri = URI.create(baseUrl + key);
        try {
            return http.execute(uri, HttpMethod.GET, request -> {
                request.getHeaders().set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
                request.getHeaders().set(HttpHeaders.REFERER, baseUrl + "/");
                request.getHeaders().setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            }, response -> {
                byte[] body = response.getBody().readNBytes(BODY_LIMIT + 1);
                if (body.length > BODY_LIMIT) {
                    throw new IllegalStateException("upstream body too large");
                }
                boolean ok = response.getStatusCode().is2xxSuccessful();
                long until = System.currentTimeMillis() + (ok ? FRESH_MS : 5_000);
                return new Cached(body, ok, until);
            });
        } catch (RuntimeException ex) {
            byte[] body = "{\"msg\":\"上游暂时不可用\",\"code\":\"502\",\"data\":null}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new Cached(body, false, System.currentTimeMillis() + 5_000);
        }
    }

    private static ResponseEntity<byte[]> json(byte[] body, boolean ok, boolean shared) {
        CacheControl cache = ok && shared
                ? CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()
                : CacheControl.noStore();
        return ResponseEntity.status(ok ? 200 : 502)
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(cache)
                .body(body);
    }

    private record Cached(byte[] body, boolean ok, long until) {
    }
}
