package com.zhiquan.sport.store.db;

import java.util.Map;
import java.util.TreeMap;

public final class DatabaseQuery {

    private DatabaseQuery() {
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
