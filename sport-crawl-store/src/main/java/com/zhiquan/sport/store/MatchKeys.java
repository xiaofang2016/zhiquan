package com.zhiquan.sport.store;

import java.time.LocalDate;

public final class MatchKeys {

    private MatchKeys() {
    }

    public static String listKey(String section, int sortOrder, long sourceId) {
        return section + "#" + pad(sortOrder, 10) + "#" + pad(sourceId, 18);
    }

    public static String dateCid(LocalDate date, Integer cid) {
        return date + "#" + (cid == null ? "" : cid);
    }

    public static String leagueSk(int sourceId, int sportType) {
        return pad(sourceId, 10) + "#" + sportType;
    }

    static String pad(long value, int width) {
        if (value < 0) {
            throw new IllegalArgumentException("negative key");
        }
        return String.format("%0" + width + "d", value);
    }
}
