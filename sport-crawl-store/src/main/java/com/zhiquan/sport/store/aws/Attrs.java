package com.zhiquan.sport.store.aws;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

public final class Attrs {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private Attrs() {
    }

    public static AttributeValue s(String value) {
        return AttributeValue.fromS(value);
    }

    public static AttributeValue n(long value) {
        return AttributeValue.fromN(Long.toString(value));
    }

    public static String str(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null) {
            return null;
        }
        return value.s();
    }

    public static Integer num(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return null;
        }
        return Integer.valueOf(value.n());
    }

    public static Long lng(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null) {
            return null;
        }
        if (value.n() != null && !value.n().isBlank()) {
            return Long.valueOf(value.n());
        }
        if (value.s() != null && !value.s().isBlank()) {
            return Long.valueOf(value.s());
        }
        return null;
    }

    public static String now() {
        return Instant.now().toString();
    }

    public static LocalDateTime time(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), SHANGHAI);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(text);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
