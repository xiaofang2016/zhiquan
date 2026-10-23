package com.zhiquan.sport.store.db;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DatabaseTime {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private DatabaseTime() {
    }

    public static String format(Instant instant) {
        return FORMAT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    public static Instant parse(String text) {
        return Instant.from(FORMAT.parse(text));
    }

    public static boolean olderThan(String fetchedAt, Duration max, Instant now) {
        if (fetchedAt == null || fetchedAt.isBlank()) {
            return true;
        }
        return DatabaseTime.parse(fetchedAt).plus(max).isBefore(now);
    }
}
