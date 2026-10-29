package com.zhiquan.sport.api.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 说球帝调吉祥、UED 时的 User-Agent。按手机号区分，避免服务器出口被当成同一个调用方。
 */
final class SportVendorAgent {

    private SportVendorAgent() {
    }

    /**
     * @param phone 当前用户手机号
     * @return 以 sport-crawl/ 开头的标识，不含明文手机号
     */
    static String forPhone(String phone) {
        String raw = phone == null ? "" : phone;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sport-crawl/");
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception ex) {
            return "sport-crawl";
        }
    }
}
