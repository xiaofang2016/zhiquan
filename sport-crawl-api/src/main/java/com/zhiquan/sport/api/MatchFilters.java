package com.zhiquan.sport.api;

import java.util.ArrayList;
import java.util.List;

final class MatchFilters {

    static final int MAX_LEAGUES = 20;

    private MatchFilters() {
    }

    static List<Integer> leagues(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            String text = part.trim();
            if (text.isEmpty() || "0".equals(text)) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("cid 格式不对");
            }
            if (id < 0) {
                throw new IllegalArgumentException("cid 格式不对");
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.size() > MAX_LEAGUES) {
            throw new IllegalArgumentException("cid 最多 20 个");
        }
        return List.copyOf(ids);
    }

    static Long team(String raw) {
        if (raw == null || raw.isBlank() || "0".equals(raw.trim())) {
            return null;
        }
        long id;
        try {
            id = Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("teamId 格式不对");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("teamId 格式不对");
        }
        return id;
    }

    static boolean anchored(Integer zhuboType) {
        return zhuboType != null && zhuboType == 1;
    }
}
