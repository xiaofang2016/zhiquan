package com.zhiquan.sport.api.user;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class UserToken {

    private UserToken() {
    }

    public record Session(String phone, String userId) {
    }

    public static String issue(String secret, String phone, String userId, long expiresAt) {
        String payload = phone + "." + userId + "." + expiresAt;
        return payload + "." + sign(secret, payload);
    }

    public static Session read(String secret, String token, long now) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("请先登录");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("请先登录");
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        byte[] actual = sign(secret, payload).getBytes(StandardCharsets.UTF_8);
        byte[] expected = parts[3].getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new IllegalArgumentException("请先登录");
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("请先登录");
        }
        if (expiresAt < now) {
            throw new IllegalArgumentException("登录已过期");
        }
        return new Session(parts[0], parts[1]);
    }

    private static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("登录状态无法签发", ex);
        }
    }
}
