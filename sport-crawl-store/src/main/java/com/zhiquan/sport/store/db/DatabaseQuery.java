package com.zhiquan.sport.store.db;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DatabaseQuery {

    /** 原站这些接口缺了对应整数就会 400。没带齐的请求不要进按需队列。 */
    private static final Map<String, List<String>> REQUIRED = Map.ofEntries(
            Map.entry("/player/getPlayerBaseInfoByPlayerId", List.of("type", "playerId")),
            Map.entry("/player/getPlayerTransferInfoByPlayerId", List.of("type", "playerId")),
            Map.entry("/player/queryBPlayerMatchInfoByPlayerIdAndOne", List.of("playerId", "countType")),
            Map.entry("/player/queryBPlayerMatchInfoByPlayerIdAndTwo", List.of("playerId", "countType")),
            Map.entry("/player/queryFPlayerCompetitionAndSeasonByPlayerId", List.of("type", "playerId")),
            Map.entry("/player/queryFPlayerCompetitionAndSeasonInfoByPlayerId", List.of("type", "playerId")),
            Map.entry("/player/queryFPlayerHonorByPlayerId", List.of("type", "playerId", "orderType")),
            Map.entry("/player/queryFPlayerInjuryByPlayerId", List.of("type", "playerId")),
            Map.entry("/player/queryFPlayerMatchByPlayerId", List.of("type", "playerId")),
            Map.entry("/player/queryFPlayerSeasonDataByPlayerId", List.of("type", "playerId", "seasonId")),
            Map.entry("/player/queryFPlayerWhitStatByPlayerId", List.of("type", "playerId")),
            Map.entry("/player/queryFPlayerWhitStatTwoByPlayerId", List.of("type", "playerId")),
            Map.entry("/team/getTeamAndPlayerStatsList", List.of("cid", "type", "statsObj", "statsType"))
    );

    /** 赛季可以是 2025，也可以是 2025-2026。 */
    private static final Map<String, List<String>> SEASONS = Map.of(
            "/team/getTeamAndPlayerStatsList", List.of("year")
    );

    private DatabaseQuery() {
    }

    public static boolean demandable(String path, Map<String, String> query) {
        List<String> names = REQUIRED.get(path);
        if (names == null) {
            return true;
        }
        Map<String, String> normalized = normalize(query);
        for (String name : names) {
            if (!integer(normalized.get(name))) {
                return false;
            }
        }
        for (String name : SEASONS.getOrDefault(path, List.of())) {
            String value = normalized.get(name);
            if (value == null || !value.matches("[0-9]{4}(-[0-9]{4})?")) {
                return false;
            }
        }
        return true;
    }

    private static boolean integer(String value) {
        return value != null && value.matches("[0-9]{1,18}");
    }

    public static Map<String, String> normalize(Map<String, String> raw) {
        TreeMap<String, String> query = new TreeMap<>();
        if (raw != null) {
            raw.forEach((key, value) -> {
                if (key == null || key.isBlank() || "token".equals(key) || value == null || value.isBlank()) {
                    return;
                }
                query.put(key, value);
            });
        }
        query.putIfAbsent("langtype", "zh");
        return query;
    }
}
