package com.zhiquan.sport.api.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 职圈打开吉祥、UED 或说球帝直播。签名原文和入账同一套，body 是用户、手机号、品牌。 */
public final class BoundLogin {

    public static final String PATH = "/prod-api/user/bound-login";

    private BoundLogin() {
    }

    public static void check(String secret, String timestamp, String nonce, String userId, String phone,
                             String brand, String signature, long now) {
        if (secret == null || secret.isBlank() || !fresh(timestamp, now) || !nonceOk(nonce)
                || !idOk(userId) || !phoneOk(phone) || brandOf(brand).isEmpty()
                || signature == null || !signature.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("签名不正确");
        }
        String expect = JxtBoximSign.sign(secret, timestamp, nonce, "POST", PATH, signed(userId, phone, brand));
        if (!MessageDigest.isEqual(expect.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("签名不正确");
        }
    }

    /**
     * 绑定还在、令牌没过期时返回令牌。否则抛出可以直接提示的中文。
     */
    public static String token(boolean bound, String accessToken, long expireAt, long now, String brand) {
        String name = "ued".equals(brand) ? "UED" : "吉祥";
        if (!bound) {
            throw new IllegalArgumentException("请先绑定" + name + "账号");
        }
        if (accessToken == null || accessToken.isBlank() || expireAt <= now) {
            throw new IllegalArgumentException("请重新验证" + name + "账号");
        }
        return accessToken;
    }

    public static String signed(String userId, String phone, String brand) {
        return userId + "\n" + phone + "\n" + brand;
    }

    private static boolean fresh(String timestamp, long now) {
        if (timestamp == null || !timestamp.matches("[0-9]{10,13}")) {
            return false;
        }
        long sent = Long.parseLong(timestamp);
        if (timestamp.length() > 10) {
            sent = sent / 1000;
        }
        return Math.abs(now - sent) <= 300;
    }

    private static boolean nonceOk(String nonce) {
        return nonce != null && nonce.matches("[A-Za-z0-9]{16,64}");
    }

    private static boolean idOk(String userId) {
        return userId != null && userId.matches("[a-f0-9]{32}");
    }

    private static boolean phoneOk(String phone) {
        return phone != null && phone.matches("1[3-9][0-9]{9}");
    }

    private static String brandOf(String brand) {
        if ("jxt".equals(brand) || "ued".equals(brand) || "live".equals(brand)) {
            return brand;
        }
        return "";
    }
}
