package com.zhiquan.sport.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

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
            "/prod-api/match/detail/tabs",
            "/prod-api/match/detail/lineupAnalysis",
            "/prod-api/match/detail/playersStatSeason",
            "/prod-api/match/detail/seasonData",
            "/prod-api/at/list"
    );

    private static final long FRESH_MS = 60_000;
    private static final int BODY_LIMIT = 2_000_000;

    private final String baseUrl;
    private final RestTemplate http;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public UpstreamMirrorController(@Value("${sport.community.base-url:https://kzb001.net}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = new RestTemplate(factory);
    }

    @GetMapping({
            "/prod-api/gift/getGiftList",
            "/prod-api/bobble/getBobbleList",
            "/prod-api/match/news/information/list",
            "/prod-api/match/detail/tabs",
            "/prod-api/match/detail/lineupAnalysis",
            "/prod-api/match/detail/playersStatSeason",
            "/prod-api/match/detail/seasonData",
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
        String key = query == null || query.isBlank() ? path : path + "?" + query;
        Cached hit = cache.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && hit.until > now) {
            return json(hit.body, hit.ok);
        }
        Cached loaded = load(key);
        if (loaded.ok) {
            cache.put(key, loaded);
        }
        return json(loaded.body, loaded.ok);
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

    private static ResponseEntity<byte[]> json(byte[] body, boolean ok) {
        CacheControl cache = ok
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
