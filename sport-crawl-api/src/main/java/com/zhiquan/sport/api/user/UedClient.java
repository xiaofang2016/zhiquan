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
public class UedClient {

    static final String MERCHANT_NO = "10012";
    private static final Logger log = LoggerFactory.getLogger(UedClient.class);
    private static final String TRANSFER_PATH = "/external/transfer/cross-platform";

    private final String baseUrl;
    private final String secretName;
    private final ObjectMapper json;
    private final HttpClient http;
    private final SecretsManagerClient secrets;
    private volatile String signingSecret;

    public UedClient(@Value("${sport.ued.base-url:}") String baseUrl,
                     @Value("${sport.ued.boxim-secret-name:prod/jxt-boxim}") String secretName,
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

    public UedAccount confirm(String phone, String code) {
        return account(postPublic("/mobilelogin", Map.of(
                "mobile", phone,
                "code", code,
                "phone_area_code", "86"
        )), phone);
    }

    public UedAccount login(String username, String password) {
        JsonNode data = postPublic("/login", Map.of(
                "username", username,
                "password", password
        ));
        if (data.path("access_token").asText("").isBlank() && "2".equals(data.path("remote_login").asText(""))) {
            throw new IllegalArgumentException("请用短信验证");
        }
        return account(data, username);
    }

    private static UedAccount account(JsonNode data, String fallbackName) {
        String uid = data.path("id").asText("");
        if (uid.isBlank()) {
            uid = text(data, "uid");
        }
        String username = data.path("username").asText("");
        if (username.isBlank()) {
            username = fallbackName;
        }
        String accessToken = data.path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("UED接口失败");
        }
        long expireAt;
        try {
            expireAt = Long.parseLong(data.path("expire_time").asText(""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("UED接口失败");
        }
        return new UedAccount(uid, username, accessToken, expireAt);
    }

    public void requireTransfer() {
        if (baseUrl.isBlank() || signingSecret().isBlank()) {
            throw new IllegalArgumentException("UED接口未配置");
        }
    }

    public void transfer(String uedUserId, String accessToken, long cents, String orderNo) {
        String secret = signingSecret();
        ObjectNode body = json.createObjectNode();
        body.put("accessToken", accessToken);
        body.put("userId", uedUserId);
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
            throw new IllegalArgumentException("UED接口未配置");
        }
        ObjectNode body = json.createObjectNode();
        body.put("merchant_no", MERCHANT_NO);
        fields.forEach((key, value) -> body.put(key, String.valueOf(value)));
        Object mobile = fields.get("mobile");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json")
                .header("user-agent", SportVendorAgent.forPhone(mobile == null ? "" : String.valueOf(mobile)))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new IllegalArgumentException("UED接口失败");
        }
        JsonNode root;
        try {
            root = json.readTree(response.body());
        } catch (Exception ex) {
            throw new IllegalArgumentException("UED接口失败");
        }
        String code = root.path("code").asText("");
        if (!"10000".equals(code) && !"0".equals(code)) {
            String message = root.path("message").asText("");
            if (message.isBlank()) {
                message = root.path("msg").asText("UED接口失败");
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
            throw new IllegalArgumentException("UED接口未配置");
        }
        try {
            var response = secrets.getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build());
            String raw = response.secretString();
            if ((raw == null || raw.isBlank()) && response.secretBinary() != null) {
                raw = response.secretBinary().asUtf8String();
            }
            String value = JxtClient.plainSecret(raw, json);
            if (value.isBlank()) {
                throw new IllegalArgumentException("UED接口未配置");
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (SecretsManagerException ex) {
            String code = ex.awsErrorDetails() == null ? "" : ex.awsErrorDetails().errorCode();
            log.warn("ued boxim secret unreadable: {}", code);
            throw new IllegalArgumentException("UED接口未配置");
        } catch (Exception ex) {
            log.warn("ued boxim secret unreadable: {}", ex.getClass().getSimpleName());
            throw new IllegalArgumentException("UED接口未配置");
        }
    }

    private static String text(JsonNode data, String name) {
        String value = data.path(name).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("UED接口失败");
        }
        return value;
    }

    public record UedAccount(String uid, String username, String accessToken, long expireAt) {
    }
}
