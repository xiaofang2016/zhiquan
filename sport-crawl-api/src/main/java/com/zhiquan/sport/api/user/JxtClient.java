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
import java.util.Map;
import java.util.UUID;

@Component
public class JxtClient {

    private static final String TRANSFER_PATH = "/external/transfer/cross-platform";

    private final String baseUrl;
    private final String secret;
    private final ObjectMapper json;
    private final HttpClient http;

    public JxtClient(@Value("${sport.jxt.base-url:}") String baseUrl,
                     @Value("${sport.jxt.boxim-secret:}") String secret,
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

    public JxtAccount confirm(String phone, String code) {
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
        String accessToken = data.path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("吉祥接口失败");
        }
        long expireAt;
        try {
            expireAt = Long.parseLong(data.path("expire_time").asText(""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("吉祥接口失败");
        }
        return new JxtAccount(uid, username, accessToken, expireAt);
    }

    public void requireTransfer() {
        if (baseUrl.isBlank() || secret.isBlank()) {
            throw new IllegalArgumentException("吉祥接口未配置");
        }
    }

    public void transfer(String jxtUserId, String accessToken, long cents, String orderNo) {
        requireTransfer();
        ObjectNode body = json.createObjectNode();
        body.put("accessToken", accessToken);
        body.put("userId", jxtUserId);
        body.put("amount", JxtMoney.yuan(cents));
        body.put("tradeNo", orderNo);
        String raw = body.toString();
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String signature = JxtBoximSign.sign(secret, timestamp, nonce, "POST", TRANSFER_PATH, raw);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + TRANSFER_PATH))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json")
                .header("user-agent", "sport-crawl")
                .header(JxtBoximSign.HEADER_TIMESTAMP, timestamp)
                .header(JxtBoximSign.HEADER_NONCE, nonce)
                .header(JxtBoximSign.HEADER_SIGNATURE, signature)
                .POST(HttpRequest.BodyPublishers.ofString(raw))
                .build();
        send(request);
    }

    private JsonNode postPublic(String path, Map<String, ?> fields) {
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("吉祥接口未配置");
        }
        ObjectNode body = json.createObjectNode();
        body.put("merchant_no", "10011");
        fields.forEach((key, value) -> body.put(key, String.valueOf(value)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json")
                .header("user-agent", "sport-crawl")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
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

    public record JxtAccount(String uid, String username, String accessToken, long expireAt) {
    }
}
