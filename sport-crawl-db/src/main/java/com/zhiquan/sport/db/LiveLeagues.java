package com.zhiquan.sport.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.entity.SportMatch;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LiveLeagues {

    private LiveLeagues() {
    }

    /**
     * 结束时间没有单独的字段，开球后 5 小时内的完场覆盖大约 2 小时比赛再加 3 小时。
     */
    public static boolean include(String phase, LocalDateTime kickoff, LocalDateTime now) {
        if ("live".equals(phase)) {
            return true;
        }
        if (!"done".equals(phase) || kickoff == null || now == null) {
            return false;
        }
        return !kickoff.isBefore(now.minusHours(5));
    }

    public static List<DatabaseCatalog.Competition> select(List<SportMatch> rows, ObjectMapper json, LocalDateTime now) {
        Map<String, DatabaseCatalog.Competition> found = new LinkedHashMap<>();
        for (SportMatch row : rows) {
            if (row.getCid() == null || row.getSportType() == null) {
                continue;
            }
            if (row.getSportType() != 1 && row.getSportType() != 2) {
                continue;
            }
            String phase = phaseOf(row, json);
            if (!include(phase, row.getMatchTime(), now)) {
                continue;
            }
            found.putIfAbsent(row.getSportType() + ":" + row.getCid(),
                    new DatabaseCatalog.Competition(row.getCid(), row.getSportType()));
        }
        return List.copyOf(found.values());
    }

    private static String phaseOf(SportMatch row, ObjectMapper json) {
        if (row.getPayload() == null || row.getPayload().isBlank()) {
            return "wait";
        }
        try {
            JsonNode node = json.readTree(row.getPayload());
            return MatchPhase.of(node.path("status_up_name").asText(""), node.path("status_up").asInt(0));
        } catch (Exception ex) {
            return "wait";
        }
    }
}
