package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NexsmsClient {

    private static final Logger log = LoggerFactory.getLogger(NexsmsClient.class);
    private static final int[] RETRY_DELAYS_MS = {1000, 2000, 4000};

    private final String url;
    private final String secretName;
    private final String template;
    private final ObjectMapper json;
    private final HttpClient http;
    private final SecretsManagerClient secrets;
    private volatile String apiKey;

    public NexsmsClient(@Value("${sport.sms.url}") String url,
                        @Value("${sport.sms.secret-name}") String secretName,
                        @Value("${sport.sms.template}") String template,
                        @Value("${sport.aws.region}") String region,
                        ObjectMapper json) {
        this.url = url;
        this.secretName = secretName;
        this.template = template;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.secrets = SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public void send(String phone, String code) {
        String apiKey = apiKey();
        String payload;
        try {
            payload = json.writeValueAsString(payload(phone, template, code));
        } catch (Exception ex) {
            throw new IllegalArgumentException("验证码发送失败");
        }
        HttpResponse<String> response = null;
        for (int attempt = 0; ; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(12))
                        .header("content-type", "application/json")
                        .header("X-API-Key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ex) {
                throw new IllegalArgumentException("验证码发送失败");
            }
            if (response.statusCode() != 503 || attempt >= RETRY_DELAYS_MS.length) {
                break;
            }
            try {
                Thread.sleep(RETRY_DELAYS_MS[attempt]);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalArgumentException("验证码发送失败");
            }
        }
        JsonNode root = read(response.body());
        int status = response.statusCode();
        String messageId = root.path("messageId").asText("");
        if (status == 202 || (status / 100 == 2 && !messageId.isBlank())) {
            return;
        }
        throw new IllegalArgumentException(failure(root));
    }

    private String apiKey() {
        String cached = apiKey;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        synchronized (this) {
            if (apiKey != null && !apiKey.isBlank()) {
                return apiKey;
            }
            apiKey = readSecret();
            return apiKey;
        }
    }

    private String readSecret() {
        try {
            var response = secrets.getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build());
            String raw = response.secretString();
            if ((raw == null || raw.isBlank()) && response.secretBinary() != null) {
                raw = response.secretBinary().asUtf8String();
            }
            return NexsmsSecret.apiKey(raw, json);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (SecretsManagerException ex) {
            String code = ex.awsErrorDetails() == null ? "" : ex.awsErrorDetails().errorCode();
            log.warn("nexsms secret unreadable: {}", code);
            throw new IllegalArgumentException("验证码发送失败");
        } catch (Exception ex) {
            log.warn("nexsms secret unreadable: {}", ex.getClass().getSimpleName());
            throw new IllegalArgumentException("验证码发送失败");
        }
    }

    static Map<String, Object> payload(String phone, String template, String code) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("code", code);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", "+86" + phone);
        body.put("templateId", template);
        body.put("variables", variables);
        return body;
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception ex) {
            return json.createObjectNode();
        }
    }

    private static String failure(JsonNode root) {
        String message = root.path("error").asText("").trim();
        if (message.isBlank() || message.length() > 80) {
            return "验证码发送失败";
        }
        return "验证码发送失败：" + message;
    }
}
