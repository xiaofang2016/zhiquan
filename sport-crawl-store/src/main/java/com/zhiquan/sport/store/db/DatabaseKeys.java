package com.zhiquan.sport.store.db;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class DatabaseKeys {

    private DatabaseKeys() {
    }

    public static String objectKey(String prefix, String path, Map<String, String> query) {
        StringBuilder key = new StringBuilder(prefix == null || prefix.isBlank() ? "database/" : prefix);
        if (key.charAt(key.length() - 1) != '/') {
            key.append('/');
        }
        String normalized = path == null ? "" : path;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("prod-api/")) {
            normalized = normalized.substring("prod-api/".length());
        }
        key.append(normalized);
        for (Map.Entry<String, String> entry : DatabaseQuery.normalize(query).entrySet()) {
            key.append('/').append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        key.append(".json");
        return key.toString();
    }

    private static String encode(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        for (byte unit : value.getBytes(StandardCharsets.UTF_8)) {
            int b = unit & 0xff;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
                    || b == '-' || b == '_' || b == '.' || b == '~') {
                encoded.append((char) b);
            } else {
                encoded.append('%').append(Character.toUpperCase(Character.forDigit(b >>> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
            }
        }
        return encoded.toString();
    }
}
