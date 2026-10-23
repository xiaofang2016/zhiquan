package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.store.ImageFields;
import com.zhiquan.sport.store.ListSection;
import com.zhiquan.sport.store.SourceHash;
import com.zhiquan.sport.store.entity.SportImage;
import com.zhiquan.sport.store.entity.SportLeague;
import com.zhiquan.sport.store.entity.SportMatch;
import com.zhiquan.sport.store.entity.SportMatchDate;
import com.zhiquan.sport.store.repo.SportImageRepository;
import com.zhiquan.sport.store.repo.SportLeagueRepository;
import com.zhiquan.sport.store.repo.SportMatchDateRepository;
import com.zhiquan.sport.store.repo.SportMatchRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class SportQueryService {

    private static final int PINNED_LIMIT = 2000;

    private final SportLeagueRepository leagueMapper;
    private final SportMatchDateRepository dateMapper;
    private final SportMatchRepository matchMapper;
    private final SportImageRepository imageMapper;
    private final ObjectMapper objectMapper;

    public SportQueryService(SportLeagueRepository leagueMapper,
                             SportMatchDateRepository dateMapper,
                             SportMatchRepository matchMapper,
                             SportImageRepository imageMapper,
                             ObjectMapper objectMapper) {
        this.leagueMapper = leagueMapper;
        this.dateMapper = dateMapper;
        this.matchMapper = matchMapper;
        this.imageMapper = imageMapper;
        this.objectMapper = objectMapper;
    }

    public List<JsonNode> leagues() {
        List<SportLeague> rows = leagueMapper.listAll();
        Map<String, String> images = localImages(rows.stream().map(SportLeague::getPayload).toList());
        List<JsonNode> result = new ArrayList<>();
        for (SportLeague row : rows) {
            JsonNode node = rewrite(row.getPayload(), images);
            if (node != null) {
                result.add(node);
            }
        }
        return result;
    }

    public List<MatchDateItem> dates() {
        List<MatchDateItem> result = new ArrayList<>();
        for (SportMatchDate row : dateMapper.listAll()) {
            result.add(new MatchDateItem(row.getMatchDate().toString(), row.getTotalMatch() == null ? 0 : row.getTotalMatch()));
        }
        return result;
    }

    public MatchListData matches(LocalDate date, Integer sportType, Integer cid, Integer hot, int size, String nextToken) {
        long total = matchMapper.count(date, ListSection.DATA, sportType, cid, hot);
        List<SportMatch> notop = matchMapper.listLimited(date, ListSection.NOTOP, sportType, cid, hot, PINNED_LIMIT);
        List<SportMatch> top = matchMapper.listLimited(date, ListSection.TOP, sportType, cid, hot, PINNED_LIMIT);
        SportMatchRepository.MatchSlice data = matchMapper.page(date, ListSection.DATA, sportType, cid, hot, size, nextToken);
        List<String> payloads = new ArrayList<>();
        notop.forEach(row -> payloads.add(row.getPayload()));
        top.forEach(row -> payloads.add(row.getPayload()));
        data.items().forEach(row -> payloads.add(row.getPayload()));
        Map<String, String> images = localImages(payloads);
        return new MatchListData(
                date.toString(),
                size,
                total,
                data.nextToken(),
                rewriteAll(notop, images),
                rewriteAll(top, images),
                rewriteAll(data.items(), images)
        );
    }

    private List<JsonNode> rewriteAll(List<SportMatch> rows, Map<String, String> images) {
        List<JsonNode> result = new ArrayList<>();
        for (SportMatch row : rows) {
            JsonNode node = rewrite(row.getPayload(), images);
            if (node != null) {
                result.add(node);
            }
        }
        return result;
    }

    private Map<String, String> localImages(Collection<String> payloads) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (String payload : payloads) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                for (String field : ImageFields.NAMES) {
                    String url = node.path(field).asText("").trim();
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        urls.add(url);
                    }
                }
            } catch (Exception ignored) {
                // skip a bad payload when collecting image urls
            }
        }
        Map<String, String> found = new HashMap<>();
        if (urls.isEmpty()) {
            return found;
        }
        List<String> hashes = urls.stream().map(SourceHash::sha256).toList();
        for (int i = 0; i < hashes.size(); i += 100) {
            List<String> chunk = hashes.subList(i, Math.min(i + 100, hashes.size()));
            for (SportImage image : imageMapper.selectByHashes(chunk)) {
                if ("ok".equals(image.getStatus()) && image.getSourceHash() != null) {
                    found.put(image.getSourceUrl(), image.getSourceHash());
                }
            }
        }
        return found;
    }

    private JsonNode rewrite(String payload, Map<String, String> images) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (!(node instanceof ObjectNode objectNode)) {
                return node;
            }
            for (String field : ImageFields.NAMES) {
                String url = objectNode.path(field).asText("");
                String hash = images.get(url);
                if (hash != null) {
                    objectNode.put(field, "/api/sport/images/" + hash);
                }
            }
            return objectNode;
        } catch (Exception ex) {
            return null;
        }
    }

    public record MatchDateItem(String matchDate, int totalMatch) {
    }

    public record MatchListData(String date, int size, long total, String nextToken,
                                List<JsonNode> notopList, List<JsonNode> topList, List<JsonNode> dataList) {
    }
}
