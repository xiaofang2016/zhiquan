package com.zhiquan.sport.store.aws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class KeyToken {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KeyToken() {
    }

    public static String encode(Map<String, AttributeValue> key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        ObjectNode node = MAPPER.createObjectNode();
        key.forEach((name, value) -> {
            ObjectNode typed = node.putObject(name);
            if (value.s() != null) {
                typed.put("S", value.s());
            } else if (value.n() != null) {
                typed.put("N", value.n());
            } else {
                throw new IllegalArgumentException("nextToken 无效");
            }
        });
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(node.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static Map<String, AttributeValue> decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(token);
            JsonNode node = MAPPER.readTree(json);
            if (!node.isObject() || node.isEmpty()) {
                throw new IllegalArgumentException("nextToken 无效");
            }
            Map<String, AttributeValue> key = new HashMap<>();
            node.fields().forEachRemaining(entry -> {
                JsonNode typed = entry.getValue();
                if (typed.hasNonNull("S")) {
                    key.put(entry.getKey(), AttributeValue.fromS(typed.get("S").asText()));
                } else if (typed.hasNonNull("N")) {
                    key.put(entry.getKey(), AttributeValue.fromN(typed.get("N").asText()));
                } else {
                    throw new IllegalArgumentException("nextToken 无效");
                }
            });
            return key;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("nextToken 无效");
        } catch (Exception ex) {
            throw new IllegalArgumentException("nextToken 无效");
        }
    }
}
