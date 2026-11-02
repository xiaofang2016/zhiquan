package com.zhiquan.sport.api.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Random;

/** 原站红包还开着时，说球帝按后台区间发一笔自己的金额。不调用原站领取。 */
public final class ChatRedpackRules {

    public static final int DEFAULT_MIN_CENT = 1;
    public static final int DEFAULT_MAX_CENT = 100;
    public static final int MAX_CENT = 200_000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private ChatRedpackRules() {
    }

    public record Packet(String walletId, boolean open) {
    }

    public static Packet read(String content) {
        JsonNode node;
        try {
            node = JSON.readTree(content == null ? "" : content);
        } catch (Exception ex) {
            throw new IllegalArgumentException("没有这个红包");
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("没有这个红包");
        }
        String walletId = node.path("walletId").asText("").trim();
        if (!walletId.matches("[0-9]{1,18}")) {
            throw new IllegalArgumentException("没有这个红包");
        }
        String status = node.path("status").asText("1").trim();
        String remain = node.path("remainNum").asText("").trim();
        boolean closed = "2".equals(status) || "3".equals(status) || "0".equals(remain);
        return new Packet(walletId, !closed);
    }

    public static void checkRange(int minCent, int maxCent) {
        if (minCent < 1 || maxCent > MAX_CENT || minCent > maxCent) {
            throw new IllegalArgumentException("金额范围不正确");
        }
    }

    public static long draw(int minCent, int maxCent, Random random) {
        checkRange(minCent, maxCent);
        if (minCent == maxCent) {
            return minCent;
        }
        return minCent + random.nextInt(maxCent - minCent + 1);
    }
}
