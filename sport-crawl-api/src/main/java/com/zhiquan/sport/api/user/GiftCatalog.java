package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;

public final class GiftCatalog {

    private GiftCatalog() {
    }

    public record Gift(long id, String name, int yinlang) {
    }

    public static Gift find(JsonNode body, long id) {
        JsonNode data = body == null ? null : body.path("data");
        if (data == null || !data.isArray()) {
            return null;
        }
        for (JsonNode item : data) {
            if (item.path("id").asLong(0) != id) {
                continue;
            }
            String name = item.path("gift_name").asText("").trim();
            int price = item.path("gift_yinlang").asInt(0);
            if (name.isEmpty() || price < 1) {
                throw new IllegalArgumentException("这个礼物不能送");
            }
            return new Gift(id, name, price);
        }
        return null;
    }

    public static int cost(int yinlang, int count) {
        if (count < 1 || count > 99 || yinlang < 1) {
            throw new IllegalArgumentException("数量不正确");
        }
        long cost = (long) yinlang * count;
        if (cost > 1_000_000) {
            throw new IllegalArgumentException("数量不正确");
        }
        return (int) cost;
    }

    public static String line(String name, int count) {
        return "送出了「" + name + "」×" + count;
    }

    public static String title(String name, int count) {
        String text = count > 1 ? "送出" + name + "×" + count : "送出" + name;
        return text.length() > 40 ? text.substring(0, 40) : text;
    }
}
