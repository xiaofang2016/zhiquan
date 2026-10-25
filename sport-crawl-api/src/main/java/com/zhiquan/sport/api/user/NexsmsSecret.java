package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class NexsmsSecret {

    private static final List<String> KEY_FIELDS = List.of(
            "apiKey", "api_key", "NEXSMS_API_KEY", "key", "secret", "token");

    private NexsmsSecret() {
    }

    static String apiKey(String secret, ObjectMapper json) {
        String text = secret == null ? "" : secret.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("验证码发送失败");
        }
        if (!text.startsWith("{")) {
            return text;
        }
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (Exception ex) {
            throw new IllegalArgumentException("验证码发送失败");
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("验证码发送失败");
        }
        for (String field : KEY_FIELDS) {
            String value = root.path(field).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        String only = null;
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            JsonNode value = fields.next().getValue();
            if (!value.isTextual() || value.asText().isBlank()) {
                continue;
            }
            if (only != null) {
                throw new IllegalArgumentException("验证码发送失败");
            }
            only = value.asText().trim();
        }
        if (only == null) {
            throw new IllegalArgumentException("验证码发送失败");
        }
        return only;
    }
}
