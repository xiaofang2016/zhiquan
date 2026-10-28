package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.db.DatabaseDoc;
import com.zhiquan.sport.store.db.DatabaseKeys;
import com.zhiquan.sport.store.db.DatabaseQuery;
import com.zhiquan.sport.store.db.DatabaseTime;
import com.zhiquan.sport.store.db.DatabaseTier;
import com.zhiquan.sport.store.repo.DatabaseDocRepository;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
public class DatabaseController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseController.class);
    private static final byte[] EMPTY = "{\"msg\":\"ok\",\"code\":\"0\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
    private static final MediaType JSON = new MediaType("application", "json", StandardCharsets.UTF_8);

    private final S3DatabaseStore objects;
    private final DatabaseDocRepository docs;
    private final SportAwsProperties aws;
    private final ObjectMapper json;

    public DatabaseController(S3DatabaseStore objects,
                              DatabaseDocRepository docs,
                              SportAwsProperties aws,
                              ObjectMapper json) {
        this.objects = objects;
        this.docs = docs;
        this.aws = aws;
        this.json = json;
    }

    @GetMapping({"/prod-api/database/**", "/prod-api/team/**", "/prod-api/player/**"})
    public ResponseEntity<byte[]> get(HttpServletRequest request) {
        String path = pathOf(request);
        Map<String, String> query = DatabaseQuery.normalize(request.getParameterMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().length == 0 ? "" : entry.getValue()[0],
                        (left, right) -> left,
                        java.util.TreeMap::new)));
        String key = DatabaseKeys.objectKey(aws.getDbPrefix(), path, query);
        byte[] body = objects.get(key);
        if (body != null) {
            refreshIfCached(key, path, query);
            return ResponseEntity.ok().contentType(JSON).cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofSeconds(60))).body(body);
        }
        if (viewer(path) && DatabaseQuery.demandable(path, query)) {
            enqueue(key, path, query);
        }
        return ResponseEntity.ok().contentType(JSON).cacheControl(org.springframework.http.CacheControl.noStore()).body(EMPTY);
    }

    private void refreshIfCached(String key, String path, Map<String, String> query) {
        try {
            DatabaseDoc doc = docs.find(key);
            if (doc != null && DatabaseTier.CACHED.equals(doc.tier())
                    && DatabaseTime.olderThan(doc.fetchedAt(), Duration.ofHours(24), Instant.now())) {
                enqueue(key, path, query);
            }
        } catch (Exception ex) {
            log.warn("资料库缓存检查失败: {}", ex.getMessage());
        }
    }

    private void enqueue(String key, String path, Map<String, String> query) {
        try {
            docs.enqueue(key, path, json.writeValueAsString(query), Instant.now());
        } catch (Exception ex) {
            log.warn("资料库按需登记失败: {}", ex.getMessage());
        }
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int index = uri.indexOf("/prod-api");
        String path = index < 0 ? uri : uri.substring(index + "/prod-api".length());
        return path.isEmpty() ? "/" : path;
    }

    private static boolean viewer(String path) {
        if (path.startsWith("/player/")) {
            return true;
        }
        return path.startsWith("/team/getTeamBase")
                || path.startsWith("/team/getTeamInfo")
                || path.startsWith("/team/getTeamData")
                || path.startsWith("/team/getTeamList")
                || path.startsWith("/team/getTeamAndPlayer")
                || path.startsWith("/team/getPlayer")
                || path.startsWith("/team/getCompetitionByTeam")
                || path.startsWith("/team/getCompetitionYearByTeam")
                || "/team/getMatchListByCid".equals(path)
                || "/team/getFifaRankMem".equals(path)
                || "/team/getFifaRankWomen".equals(path)
                || "/team/getHonorHistory".equals(path);
    }
}
