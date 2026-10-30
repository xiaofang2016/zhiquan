package com.zhiquan.sport.api.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 职圈群红包扣款、入账、退回。签名原文是用户、手机号、方向、分、单号、标题。 */
public final class GroupRedpack {

    public static final String PATH = "/prod-api/user/group-redpack";

    private GroupRedpack() {
    }

    public static void check(String secret, String timestamp, String nonce, String userId, String phone,
                             String direction, String cents, String tradeNo, String title, String signature, long now) {
        if (secret == null || secret.isBlank() || !fresh(timestamp, now) || !nonceOk(nonce)
                || !idOk(userId) || !phoneOk(phone) || !directionOk(direction) || !centsOk(cents)
                || !tradeOk(tradeNo) || !titleOk(title)
                || signature == null || !signature.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("签名不正确");
        }
        String expect = JxtBoximSign.sign(secret, timestamp, nonce, "POST", PATH,
                signed(userId, phone, direction, cents, tradeNo, title));
        if (!MessageDigest.isEqual(expect.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("签名不正确");
        }
    }

    public static String signed(String userId, String phone, String direction, String cents, String tradeNo, String title) {
        return userId + "\n" + phone + "\n" + direction + "\n" + cents + "\n" + tradeNo + "\n" + title;
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

    private static boolean directionOk(String direction) {
        return "debit".equals(direction) || "credit".equals(direction);
    }

    private static boolean centsOk(String cents) {
        return cents != null && cents.matches("[1-9][0-9]{0,7}") && Long.parseLong(cents) <= 200_000L;
    }

    private static boolean tradeOk(String tradeNo) {
        return tradeNo != null && tradeNo.matches("[A-Za-z0-9-]{8,80}");
    }

    private static boolean titleOk(String title) {
        return "发红包".equals(title) || "群红包".equals(title) || "红包退回".equals(title);
    }
}
