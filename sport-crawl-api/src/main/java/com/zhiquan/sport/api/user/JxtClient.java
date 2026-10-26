package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class JxtClient {

    private static final Logger log = LoggerFactory.getLogger(JxtClient.class);
    private static final String TRANSFER_PATH = "/external/transfer/cross-platform";

    private final String baseUrl;
    private final String secretName;
    private final ObjectMapper json;
    private final HttpClient http;
    private final SecretsManagerClient secrets;
    private volatile String signingSecret;

    public JxtClient(@Value("${sport.jxt.base-url:}") String baseUrl,
                     @Value("${sport.jxt.boxim-secret-name:prod/jxt-boxim}") String secretName,
                     @Value("${sport.aws.region}") String region,
                     ObjectMapper json) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.secretName = secretName == null ? "" : secretName;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.secrets = SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
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
        if (baseUrl.isBlank() || signingSecret().isBlank()) {
            throw new IllegalArgumentException("吉祥接口未配置");
        }
    }

    public void transfer(String jxtUserId, String accessToken, long cents, String orderNo) {
        String secret = signingSecret();
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

    private String signingSecret() {
        String cached = signingSecret;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        synchronized (this) {
            if (signingSecret != null && !signingSecret.isBlank()) {
                return signingSecret;
            }
            signingSecret = readSecret();
            return signingSecret;
        }
    }

    private String readSecret() {
        if (secretName.isBlank()) {
            throw new IllegalArgumentException("吉祥接口未配置");
        }
        try {
            var response = secrets.getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build());
            String raw = response.secretString();
            if ((raw == null || raw.isBlank()) && response.secretBinary() != null) {
                raw = response.secretBinary().asUtf8String();
            }
            String value = plainSecret(raw, json);
            if (value.isBlank()) {
                throw new IllegalArgumentException("吉祥接口未配置");
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (SecretsManagerException ex) {
            String code = ex.awsErrorDetails() == null ? "" : ex.awsErrorDetails().errorCode();
            log.warn("jxt boxim secret unreadable: {}", code);
            throw new IllegalArgumentException("吉祥接口未配置");
        } catch (Exception ex) {
            log.warn("jxt boxim secret unreadable: {}", ex.getClass().getSimpleName());
            throw new IllegalArgumentException("吉祥接口未配置");
        }
    }

    static String plainSecret(String secret, ObjectMapper json) {
        String text = secret == null ? "" : secret.trim();
        if (!text.startsWith("{")) {
            return text;
        }
        try {
            JsonNode root = json.readTree(text);
            for (String field : new String[]{"BOXIM_OUTBOUND_SECRET", "secret", "token"}) {
                String value = root.path(field).asText("").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception ex) {
            return "";
        }
        return "";
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
