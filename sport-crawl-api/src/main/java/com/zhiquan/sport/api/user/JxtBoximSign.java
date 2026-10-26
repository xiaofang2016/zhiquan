package com.zhiquan.sport.api.user;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** 和吉祥红包入账用同一套原文：时间戳、随机串、方法、路径、原始 body，各段用换行接上。 */
public final class JxtBoximSign {

    public static final String HEADER_TIMESTAMP = "X-Boxim-Timestamp";
    public static final String HEADER_NONCE = "X-Boxim-Nonce";
    public static final String HEADER_SIGNATURE = "X-Boxim-Signature";

    private JxtBoximSign() {
    }

    public static String payload(String timestamp, String nonce, String method, String path, String body) {
        return timestamp + "\n" + nonce + "\n" + method.toUpperCase() + "\n" + path + "\n" + body;
    }

    public static String sign(String secret, String timestamp, String nonce, String method, String path, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload(timestamp, nonce, method, path, body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("吉祥签名失败", ex);
        }
    }
}
