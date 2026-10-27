package com.zhiquan.sport.db;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 把上游帖子收成页面要用的字段。非公开帖子丢掉。 */
public final class CommunityCards {

    private CommunityCards() {
    }

    public static boolean visible(JsonNode post) {
        if (post == null || !post.path("postId").canConvertToLong()) {
            return false;
        }
        if (presentAndNot(post, "auditStatus", "1")) {
            return false;
        }
        return !presentAndNot(post, "viewPermission", "0");
    }

    public static String stamp(JsonNode post) {
        return post.path("lastCommentTime").asText("")
                + "|" + post.path("commentCount").asInt(0)
                + "|" + post.path("likeCount").asInt(0);
    }

    public static Map<String, Object> post(JsonNode node) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("postId", node.path("postId").asLong());
        card.put("title", text(node, "title"));
        card.put("content", text(node, "content"));
        card.put("createTime", text(node, "createTime"));
        card.put("nickname", text(node, "nickname"));
        card.put("head", text(node, "head"));
        card.put("badgeName", text(node, "badgeName"));
        card.put("likeCount", node.path("likeCount").asInt(0));
        card.put("commentCount", node.path("commentCount").asInt(0));
        card.put("viewCount", node.path("viewCount").asInt(0));
        List<Long> circleIds = new ArrayList<>();
        String circleName = "";
        for (JsonNode circle : node.path("circles")) {
            if (!circle.path("circleId").canConvertToLong()) {
                continue;
            }
            circleIds.add(circle.path("circleId").asLong());
            if (circleName.isEmpty()) {
                circleName = text(circle, "circleName");
            }
        }
        card.put("circleIds", circleIds);
        card.put("circleName", circleName);
        List<String> images = new ArrayList<>();
        for (JsonNode media : node.path("mediaUrls")) {
            String type = media.path("type").asText("");
            String url = text(media, "url");
            if (!url.isEmpty() && (type.isEmpty() || "image".equals(type))) {
                images.add(url);
            }
        }
        card.put("images", images);
        card.put("redPacket", packet(node.path("redPacket")));
        return card;
    }

    public static Map<String, Object> circle(JsonNode node) {
        Map<String, Object> circle = new LinkedHashMap<>();
        circle.put("id", node.path("circleId").asLong());
        circle.put("name", text(node, "circleName"));
        circle.put("icon", text(node, "icon"));
        return circle;
    }

    public static Map<String, Object> comment(JsonNode node) {
        Map<String, Object> comment = new LinkedHashMap<>();
        comment.put("commentId", node.path("commentId").asLong());
        comment.put("nickname", text(node, "nickname"));
        comment.put("head", text(node, "head"));
        comment.put("badgeName", text(node, "badgeName"));
        comment.put("content", text(node, "content"));
        comment.put("createTime", text(node, "createTime"));
        comment.put("cityName", text(node, "cityName"));
        comment.put("likeCount", node.path("likeCount").asInt(0));
        return comment;
    }

    private static Map<String, Object> packet(JsonNode node) {
        if (!node.isObject() || node.path("num").asInt(0) <= 0) {
            return null;
        }
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("amount", text(node, "amount"));
        packet.put("num", node.path("num").asInt(0));
        packet.put("remainNum", node.path("remainNum").asInt(0));
        packet.put("statusName", text(node, "statusName"));
        return packet;
    }

    private static boolean presentAndNot(JsonNode node, String field, String expected) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        return !expected.equals(value.asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("");
    }
}
