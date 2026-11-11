package com.zhiquan.sport.worker;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

final class LiveLine {

    private static final List<String> FIELDS = List.of(
            "live_urls", "mirror_live_urls", "global_live_urls", "live_cartoon_url");

    private LiveLine() {
    }

    static boolean anchor(JsonNode item) {
        if (item == null || !item.isObject()) {
            return false;
        }
        for (String field : FIELDS) {
            JsonNode lines = item.get(field);
            if (lines == null || !lines.isArray()) {
                continue;
            }
            for (JsonNode line : lines) {
                if (line != null && line.isObject() && live(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean live(JsonNode line) {
        String url = line.path("url").asText("").trim();
        if (url.isEmpty() || "动画".equals(line.path("name").asText(""))) {
            return false;
        }
        return !url.contains("nami.com") && !url.contains("livetracker");
    }
}
