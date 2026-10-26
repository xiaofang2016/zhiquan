package com.zhiquan.sport.api.user;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 和吉祥 {@code JxLiveBindSign} 用同一套原文：动作名、换行、再把各段用换行接上。 */
public final class JxtSign {

    private JxtSign() {
    }

    public static String payload(String action, String... parts) {
        return action + "\n" + String.join("\n", parts);
    }

    public static String sign(String secret, String action, String... parts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload(action, parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("签名失败");
        }
    }
}
