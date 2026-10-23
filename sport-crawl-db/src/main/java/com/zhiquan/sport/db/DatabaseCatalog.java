package com.zhiquan.sport.db;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DatabaseCatalog {

    private DatabaseCatalog() {
    }

    public record Competition(int id, int sportsType) {
    }

    public static List<Competition> competitions(JsonNode data) {
        Map<String, Competition> found = new LinkedHashMap<>();
        collect(data, found);
        return List.copyOf(found.values());
    }

    public static String currentYear(JsonNode data) {
        if (data == null || !data.isArray()) {
            return "";
        }
        String first = "";
        for (JsonNode item : data) {
            String year = item.path("year").asText("");
            if (first.isEmpty()) {
                first = year;
            }
            if (item.path("is_current").asInt(0) == 1 && !year.isEmpty()) {
                return year;
            }
        }
        return first;
    }

    public static List<String> otherYears(JsonNode data, String current) {
        if (data == null || !data.isArray()) {
            return List.of();
        }
        List<String> years = new ArrayList<>();
        for (JsonNode item : data) {
            String year = item.path("year").asText("");
            if (!year.isEmpty() && !year.equals(current)) {
                years.add(year);
            }
        }
        return years;
    }

    public static long latestPubTime(JsonNode data) {
        if (data == null || !data.isArray() || data.isEmpty()) {
            return 0;
        }
        return data.get(0).path("pub_time").asLong(0);
    }

    public static List<Long> olderPubTimes(JsonNode data) {
        List<Long> times = new ArrayList<>();
        if (data == null || !data.isArray() || data.size() < 2) {
            return times;
        }
        for (int i = 1; i < data.size(); i++) {
            long time = data.get(i).path("pub_time").asLong(0);
            if (time > 0) {
                times.add(time);
            }
        }
        return times;
    }

    public static List<DatabasePlan.Stage> currentStages(JsonNode data) {
        if (data == null || !data.isArray()) {
            return List.of();
        }
        List<DatabasePlan.Stage> stages = new ArrayList<>();
        for (JsonNode item : data) {
            if (item.path("isCurStage").asInt(0) != 1) {
                continue;
            }
            long stageId = item.path("stageId").asLong(0);
            int round = item.path("curRound").asInt(0);
            if (stageId > 0 && round > 0) {
                stages.add(new DatabasePlan.Stage(stageId, round));
            }
        }
        return stages;
    }

    private static void collect(JsonNode node, Map<String, Competition> found) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            int id = node.path("id").asInt(0);
            int type = node.path("sportsType").asInt(0);
            if (id > 0 && (type == 1 || type == 2)) {
                found.putIfAbsent(type + ":" + id, new Competition(id, type));
            }
            node.properties().forEach(entry -> collect(entry.getValue(), found));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collect(child, found));
        }
    }
}
