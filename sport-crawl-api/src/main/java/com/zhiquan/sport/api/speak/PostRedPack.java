package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.api.user.JxtMoney;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/** 说球帝自己发的帖子红包。每帖 10 份、共 10 元，不使用原站金额。 */
public final class PostRedPack {

    public static final int COUNT = 10;
    public static final long POOL_CENT = 1_000L;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PostRedPack() {
    }

    public static boolean offered(JsonNode post) {
        return post != null && post.path("redPacket").isObject();
    }

    /**
     * 记下这个人领到的金额。已经领过就返回原来的金额。
     *
     * @return 分
     */
    public static long take(ObjectNode board, String postId, String userId, Random random) {
        ArrayNode claims = claims(board, postId);
        for (JsonNode claim : claims) {
            if (userId.equals(claim.path("userId").asText())) {
                return claim.path("cent").asLong();
            }
        }
        if (claims.size() >= COUNT) {
            throw new IllegalArgumentException("红包已领完");
        }
        long used = 0;
        for (JsonNode claim : claims) {
            used += claim.path("cent").asLong();
        }
        long cent = draw(POOL_CENT - used, COUNT - claims.size(), random);
        ObjectNode row = claims.addObject();
        row.put("userId", userId);
        row.put("cent", cent);
        row.put("time", LocalDateTime.now(SHANGHAI).format(STAMP));
        return cent;
    }

    public static void apply(ObjectNode post, JsonNode board, String viewer) {
        if (!offered(post)) {
            return;
        }
        String postId = post.path("postId").asText();
        JsonNode claims = board == null ? null : board.path("packets").path(postId);
        int taken = claims != null && claims.isArray() ? claims.size() : 0;
        int remain = Math.max(0, COUNT - taken);
        Long mine = mine(claims, viewer);
        ObjectNode packet = post.putObject("redPacket");
        packet.put("amount", JxtMoney.yuan(POOL_CENT));
        packet.put("num", COUNT);
        packet.put("remainNum", remain);
        packet.put("status", remain > 0 ? "1" : "2");
        packet.put("statusName", remain > 0 ? "领取中" : "已领完");
        packet.put("description", "拼手气红包");
        if (mine == null) {
            packet.putNull("mine");
        } else {
            packet.put("mine", JxtMoney.yuan(mine));
        }
    }

    private static ArrayNode claims(ObjectNode board, String postId) {
        ObjectNode packets;
        if (board.get("packets") instanceof ObjectNode existing) {
            packets = existing;
        } else {
            packets = board.putObject("packets");
        }
        if (packets.get(postId) instanceof ArrayNode array) {
            return array;
        }
        return packets.putArray(postId);
    }

    private static Long mine(JsonNode claims, String viewer) {
        if (claims == null || !claims.isArray() || viewer == null || viewer.isBlank()) {
            return null;
        }
        for (JsonNode claim : claims) {
            if (viewer.equals(claim.path("userId").asText())) {
                return claim.path("cent").asLong();
            }
        }
        return null;
    }

    private static long draw(long remainCent, int remainCount, Random random) {
        if (remainCount <= 1) {
            return remainCent;
        }
        long max = remainCent - (remainCount - 1L);
        long cap = Math.max(1L, (remainCent / remainCount) * 2);
        long upper = Math.min(max, cap);
        if (upper < 1) {
            upper = 1;
        }
        return 1 + random.nextInt((int) upper);
    }
}
