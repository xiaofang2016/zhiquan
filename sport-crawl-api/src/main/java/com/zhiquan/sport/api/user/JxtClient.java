package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JxtClient {

    private final String baseUrl;
    private final String secret;
    private final ObjectMapper json;
    private final HttpClient http;

    public JxtClient(@Value("${sport.jxt.base-url:}") String baseUrl,
                     @Value("${sport.jxt.bind-secret:}") String secret,
                     ObjectMapper json) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.secret = secret == null ? "" : secret;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    public void sms(String phone) {
        postPublic("/smscode", Map.of(
                "mobile", phone,
                "phone_area_code", "86"
        ));
    }

    public JxtAccount confirm(String phone, String code, String sportUserId) {
        JsonNode data = postPublic("/mobilelogin", Map.of(
                "mobile", phone,
                "code", code,
                "phone_area_code", "86"
        ));
        String uid = data.path("id").asText("");
        if (uid.isBlank()) {
            uid = text(data, "uid");
        }
        String username = data.path("username").asText("");
        if (username.isBlank()) {
            username = phone;
        }
        return new JxtAccount(uid, username);
    }

    public void unbind(String sportUserId) {
        post("/api/liveBindUnbind", "unbind", Map.of("sportUserId", sportUserId), sportUserId);
    }

    public void transfer(String sportUserId, long cents, String orderNo) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sportUserId", sportUserId);
        fields.put("amountCent", cents);
        fields.put("orderNo", orderNo);
        post("/api/liveTransfer", "transfer", fields, sportUserId, Long.toString(cents), orderNo);
    }

    private JsonNode postPublic(String path, Map<String, ?> fields) {
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("吉祥接口未配置");
        }
        ObjectNode body = json.createObjectNode();
        body.put("merchant_no", "10011");
        fields.forEach((key, value) -> body.put(key, String.valueOf(value)));
        return send(path, body);
    }

    private JsonNode post(String path, String action, Map<String, ?> fields, String... parts) {
        if (baseUrl.isBlank() || secret.isBlank()) {
            throw new IllegalArgumentException("吉祥接口未配置");
        }
        ObjectNode body = json.createObjectNode();
        body.put("merchant_no", "10011");
        body.put("sign", JxtSign.sign(secret, action, parts));
        fields.forEach((key, value) -> {
            if (value instanceof Long number) {
                body.put(key, number);
            } else {
                body.put(key, String.valueOf(value));
            }
        });
        return send(path, body);
    }

    private JsonNode send(String path, ObjectNode body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new IllegalArgumentException("吉祥接口失败");
        }
        JsonNode root;
        try {
            root = json.readTree(response.body());
        } catch (Exception ex) {
            throw new IllegalArgumentException("吉祥接口失败");
        }
        String code = root.path("code").asText("");
        if (!"10000".equals(code) && !"0".equals(code)) {
            String message = root.path("message").asText("");
            if (message.isBlank()) {
                message = root.path("msg").asText("吉祥接口失败");
            }
            throw new IllegalArgumentException(message);
        }
        return root.path("data");
    }

    private static String text(JsonNode data, String name) {
        String value = data.path(name).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("吉祥接口失败");
        }
        return value;
    }

    public record JxtAccount(String uid, String username) {
    }
}
