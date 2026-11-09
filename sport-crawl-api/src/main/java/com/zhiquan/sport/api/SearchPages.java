package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 搜索历史和比赛搜索翻页。比赛搜索上游第 2 页是空的，总数改成实际条数。 */
public final class SearchPages {

    static final int LIMIT = 20;

    private SearchPages() {
    }

    public static String param(String query, String name) {
        if (query == null || query.isBlank() || name == null) {
            return "";
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            String key = decode(eq < 0 ? part : part.substring(0, eq));
            if (!name.equals(key)) {
                continue;
            }
            return decode(eq < 0 ? "" : part.substring(eq + 1)).trim();
        }
        return "";
    }

    /** 不把说球帝令牌交给上游。 */
    public static String withoutAccount(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            String key = decode(eq < 0 ? part : part.substring(0, eq));
            if ("token".equals(key) || "uid".equals(key) || key.isBlank()) {
                continue;
            }
            if (!kept.isEmpty()) {
                kept.append('&');
            }
            kept.append(part);
        }
        return kept.toString();
    }

    public static String word(String raw) {
        if (raw == null) {
            return "";
        }
        String word = raw.trim();
        if (word.isEmpty() || word.length() > 40) {
            return "";
        }
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) < 0x20) {
                return "";
            }
        }
        return word;
    }

    public static List<String> keys(String raw, ObjectMapper json) {
        List<String> keys = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return keys;
        }
        try {
            JsonNode node = json.readTree(raw).path("keys");
            if (!node.isArray()) {
                return keys;
            }
            for (JsonNode item : node) {
                String key = word(item.asText(""));
                if (!key.isEmpty() && !keys.contains(key)) {
                    keys.add(key);
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return keys;
    }

    public static List<String> remember(List<String> current, String key) {
        List<String> next = new ArrayList<>();
        next.add(key);
        if (current != null) {
            for (String item : current) {
                if (key.equals(item) || next.size() >= LIMIT) {
                    continue;
                }
                next.add(item);
            }
        }
        return next;
    }

    public static String withPage(String query, int page) {
        String source = query == null ? "" : query;
        StringBuilder kept = new StringBuilder();
        for (String part : source.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = decode(eq < 0 ? part : part.substring(0, eq));
            if ("pn".equals(key)) {
                continue;
            }
            if (!kept.isEmpty()) {
                kept.append('&');
            }
            kept.append(part);
        }
        if (!kept.isEmpty()) {
            kept.append('&');
        }
        kept.append("pn=").append(page);
        return kept.toString();
    }

    public static boolean laterPageEmpty(byte[] body, ObjectMapper json) {
        try {
            JsonNode list = json.readTree(body).path("data").path("dataList");
            return !list.isArray() || list.isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean matchFirstPage(String query, byte[] body, ObjectMapper json) {
        if (!"1".equals(param(query, "tab"))) {
            return false;
        }
        String pn = param(query, "pn");
        if (!pn.isEmpty() && !"1".equals(pn)) {
            return false;
        }
        try {
            JsonNode data = json.readTree(body).path("data");
            return data.path("totalPage").asInt(1) > 1 && data.path("dataList").isArray() && !data.path("dataList").isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    public static byte[] clamp(byte[] body, ObjectMapper json) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isObject() || !data.path("dataList").isArray()) {
                return body;
            }
            int rows = data.path("dataList").size();
            ((ObjectNode) data).put("total", rows);
            ((ObjectNode) data).put("totalPage", 1);
            ((ObjectNode) data).put("currentPage", 1);
            return json.writeValueAsBytes(root);
        } catch (Exception ex) {
            return body;
        }
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return raw;
        }
    }
}
