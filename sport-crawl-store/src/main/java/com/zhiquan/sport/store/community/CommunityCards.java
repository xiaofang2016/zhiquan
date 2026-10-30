package com.zhiquan.sport.store.community;

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
                + "|" + post.path("likeCount").asInt(0)
                + "|" + post.path("viewCount").asInt(0)
                + "|" + post.path("favoriteCount").asInt(0)
                + "|v3";
    }

    /** 顶层评论加回复。和接口里能看到的条数一致。 */
    public static int shown(List<Map<String, Object>> comments) {
        int count = 0;
        if (comments == null) {
            return 0;
        }
        for (Map<String, Object> comment : comments) {
            count++;
            Object replies = comment.get("replies");
            if (replies instanceof List<?> list) {
                count += list.size();
            }
        }
        return count;
    }

    public static String repair(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.replace("cos.apiguangzhou.myqcloud.com", "cos.ap-guangzhou.myqcloud.com")
                .replace("cos.apguangzhou.myqcloud.com", "cos.ap-guangzhou.myqcloud.com");
    }

    public static Map<String, Object> post(JsonNode node) {
        return post(node, "https://kzb001.net");
    }

    /**
     * @param node 上游帖子
     * @param baseUrl 相对地址补全用的站点，视频地址有时只有路径
     * @return 页面用的帖子
     */
    public static Map<String, Object> post(JsonNode node, String baseUrl) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("postId", node.path("postId").asLong());
        card.put("title", text(node, "title"));
        card.put("content", text(node, "content"));
        card.put("createTime", text(node, "createTime"));
        card.put("lastReplyTime", text(node, "lastCommentTime"));
        card.put("nickname", text(node, "nickname"));
        card.put("head", repair(text(node, "head")));
        card.put("userId", node.path("userId").canConvertToLong() ? node.path("userId").asLong() : null);
        card.put("badgeName", text(node, "badgeName"));
        card.put("badgeLevel", node.path("badgeLevel").asInt(0));
        card.put("badgeStyle", style(node));
        card.put("vipLevel", level(node, "uedLevel"));
        card.put("jxfLevel", level(node, "jxfLevel"));
        card.put("uedLevel", level(node, "uedLevel"));
        card.put("cityName", city(node));
        card.put("likeCount", node.path("likeCount").asInt(0));
        card.put("commentCount", node.path("commentCount").asInt(0));
        card.put("viewCount", node.path("viewCount").asInt(0));
        card.put("collectCount", node.path("favoriteCount").asInt(0));
        card.put("essence", "1".equals(text(node, "isEssence")));
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
        card.put("topics", topics(node.path("topics"), false));
        List<String> images = new ArrayList<>();
        List<Map<String, Object>> pictures = new ArrayList<>();
        List<Map<String, Object>> videos = new ArrayList<>();
        for (JsonNode media : node.path("mediaUrls")) {
            String type = media.path("type").asText("");
            String url = absolute(text(media, "url"), baseUrl);
            if (url.isEmpty()) {
                continue;
            }
            if ("video".equals(type)) {
                Map<String, Object> video = new LinkedHashMap<>();
                video.put("url", url);
                video.put("cover", repair(text(media, "cover")));
                video.put("w", positive(media, "width"));
                video.put("h", positive(media, "height"));
                video.put("duration", positive(media, "duration"));
                videos.add(video);
                continue;
            }
            if (type.isEmpty() || "image".equals(type)) {
                images.add(url);
                Map<String, Object> picture = new LinkedHashMap<>();
                picture.put("url", url);
                picture.put("thumb", thumb(url));
                picture.put("w", positive(media, "width"));
                picture.put("h", positive(media, "height"));
                pictures.add(picture);
            }
        }
        card.put("images", images);
        card.put("pictures", pictures);
        card.put("videos", videos);
        card.put("match", match(node.path("matches")));
        card.put("hotComment", hot(node.path("topHighlightComment")));
        card.put("highlightCommentCount", node.path("highlightCommentCount").asInt(0));
        card.put("mentionUsers", mentions(node.path("mentionUsers")));
        card.put("rewardCount", node.path("rewardCount").asInt(0));
        card.put("rewardEnabled", enabled(node.get("rewardEnabled")));
        card.put("redPacket", packet(node.path("redPacket")));
        return card;
    }

    public static Map<String, Object> circle(JsonNode node) {
        Map<String, Object> circle = new LinkedHashMap<>();
        circle.put("id", node.path("circleId").asLong());
        circle.put("name", text(node, "circleName"));
        circle.put("icon", text(node, "icon"));
        circle.put("cover", text(node, "cover"));
        circle.put("followCount", node.path("followCount").asInt(0));
        circle.put("postCount", node.path("postCount").asInt(0));
        circle.put("ownerName", text(node, "groupAdminNickname"));
        circle.put("ownerHead", repair(text(node, "groupAdminHead")));
        circle.put("topics", topics(node.path("topics"), true));
        circle.put("announces", announces(node.path("announces")));
        return circle;
    }

    /**
     * 上游 commentCount 含楼中楼。这里的一条是顶层评论，回复在 replies。
     *
     * @return 审核未过时为空
     */
    public static Map<String, Object> comment(JsonNode node) {
        return comment(node, "https://kzb001.net");
    }

    public static Map<String, Object> comment(JsonNode node, String baseUrl) {
        if (node == null || !node.path("commentId").canConvertToLong() || presentAndNot(node, "auditStatus", "1")) {
            return null;
        }
        Map<String, Object> comment = person(node, baseUrl);
        comment.put("commentId", node.path("commentId").asLong());
        List<Map<String, Object>> replies = new ArrayList<>();
        for (JsonNode reply : node.path("replies")) {
            Map<String, Object> mapped = reply(reply, baseUrl);
            if (mapped != null) {
                replies.add(mapped);
            }
        }
        comment.put("replyCount", node.path("replyCount").asInt(replies.size()));
        comment.put("replies", replies);
        return comment;
    }

    public static Map<String, Object> reply(JsonNode node) {
        return reply(node, "https://kzb001.net");
    }

    public static Map<String, Object> reply(JsonNode node, String baseUrl) {
        if (node == null || !node.path("replyId").canConvertToLong() || presentAndNot(node, "auditStatus", "1")) {
            return null;
        }
        Map<String, Object> reply = person(node, baseUrl);
        reply.put("replyId", node.path("replyId").asLong());
        reply.put("replyToNickname", text(node, "replyToNickname"));
        if (node.path("replyToUserId").canConvertToLong()) {
            reply.put("replyToUserId", node.path("replyToUserId").asLong());
        }
        return reply;
    }

    private static Map<String, Object> person(JsonNode node, String baseUrl) {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("userId", node.path("userId").canConvertToLong() ? node.path("userId").asLong() : null);
        person.put("nickname", text(node, "nickname"));
        person.put("head", repair(text(node, "head")));
        person.put("badgeName", text(node, "badgeName"));
        person.put("badgeLevel", node.path("badgeLevel").asInt(0));
        person.put("badgeStyle", style(node));
        person.put("vipLevel", level(node, "uedLevel"));
        person.put("jxfLevel", level(node, "jxfLevel"));
        person.put("uedLevel", level(node, "uedLevel"));
        person.put("content", text(node, "content"));
        person.put("createTime", text(node, "createTime"));
        person.put("cityName", city(node));
        person.put("likeCount", node.path("likeCount").asInt(0));
        person.put("imageUrls", imageUrls(node.path("imageUrls"), baseUrl));
        return person;
    }

    private static Map<String, Object> hot(JsonNode node) {
        if (!node.isObject() || text(node, "content").isEmpty() || presentAndNot(node, "auditStatus", "1")) {
            return null;
        }
        Map<String, Object> hot = new LinkedHashMap<>();
        if (node.path("commentId").canConvertToLong()) {
            hot.put("commentId", node.path("commentId").asLong());
        }
        hot.put("userId", node.path("userId").canConvertToLong() ? node.path("userId").asLong() : null);
        hot.put("nickname", text(node, "nickname"));
        hot.put("head", repair(text(node, "head")));
        hot.put("createTime", text(node, "createTime"));
        hot.put("content", text(node, "content"));
        hot.put("likeCount", node.path("likeCount").asInt(0));
        hot.put("cityName", city(node));
        return hot;
    }

    private static Map<String, Object> match(JsonNode matches) {
        if (!matches.isArray() || matches.isEmpty()) {
            return null;
        }
        JsonNode node = matches.get(0);
        if (!node.path("matchId").canConvertToLong()) {
            return null;
        }
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("id", node.path("matchId").asLong());
        match.put("type", node.path("type").asInt(0));
        match.put("home", text(node, "homeTeamName"));
        match.put("away", text(node, "awayTeamName"));
        match.put("homeLogo", repair(text(node, "homeTeamLogo")));
        match.put("awayLogo", repair(text(node, "awayTeamLogo")));
        match.put("score", null);
        match.put("status", null);
        return match;
    }

    private static List<Map<String, Object>> topics(JsonNode nodes, boolean cover) {
        List<Map<String, Object>> topics = new ArrayList<>();
        if (!nodes.isArray()) {
            return topics;
        }
        for (JsonNode node : nodes) {
            if (!node.path("topicId").canConvertToLong()) {
                continue;
            }
            Map<String, Object> topic = new LinkedHashMap<>();
            topic.put("id", node.path("topicId").asLong());
            topic.put("name", text(node, "topicName"));
            if (cover && !text(node, "cover").isEmpty()) {
                topic.put("cover", text(node, "cover"));
            }
            topics.add(topic);
        }
        return topics;
    }

    private static Map<String, Object> packet(JsonNode node) {
        if (!node.isObject() || node.path("num").asInt(0) <= 0) {
            return null;
        }
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("walletId", text(node, "walletId"));
        packet.put("amount", text(node, "amount"));
        packet.put("num", node.path("num").asInt(0));
        packet.put("remainNum", node.path("remainNum").asInt(0));
        packet.put("status", text(node, "status"));
        packet.put("statusName", text(node, "statusName"));
        packet.put("description", text(node, "description"));
        JsonNode drawn = node.get("drawn");
        if (drawn == null || drawn.isNull()) {
            packet.put("drawn", null);
        } else {
            packet.put("drawn", drawn.asBoolean(false) || "1".equals(drawn.asText()));
        }
        String got = text(node, "drawnAmount");
        packet.put("drawnAmount", got.isEmpty() ? null : got);
        return packet;
    }

    private static boolean presentAndNot(JsonNode node, String field, String expected) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        return !expected.equals(value.asText());
    }

    private static String city(JsonNode node) {
        String city = text(node, "cityName");
        return city.isEmpty() ? text(node, "locationText") : city;
    }

    private static Integer style(JsonNode node) {
        Integer value = level(node, "badgeStyle");
        if (value == null || (value != 1 && value != 2)) {
            return null;
        }
        return value;
    }

    private static boolean enabled(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        String text = value.asText("");
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static List<String> imageUrls(JsonNode nodes, String baseUrl) {
        List<String> urls = new ArrayList<>();
        if (!nodes.isArray()) {
            return urls;
        }
        for (JsonNode node : nodes) {
            String raw = node.isTextual() ? node.asText("") : text(node, "url");
            String url = absolute(raw, baseUrl);
            if (!url.isEmpty()) {
                urls.add(url);
            }
        }
        return urls;
    }

    private static List<Map<String, Object>> mentions(JsonNode nodes) {
        List<Map<String, Object>> people = new ArrayList<>();
        if (!nodes.isArray()) {
            return people;
        }
        for (JsonNode node : nodes) {
            if (!node.isObject()) {
                continue;
            }
            Long userId = node.path("userId").canConvertToLong() ? node.path("userId").asLong() : null;
            String nickname = text(node, "nickname");
            if (nickname.isEmpty()) {
                nickname = text(node, "nickName");
            }
            if (userId == null && nickname.isEmpty()) {
                continue;
            }
            Map<String, Object> person = new LinkedHashMap<>();
            person.put("userId", userId);
            person.put("nickname", nickname);
            people.add(person);
        }
        return people;
    }

    private static List<Map<String, Object>> announces(JsonNode nodes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!nodes.isArray()) {
            return rows;
        }
        for (JsonNode node : nodes) {
            if (!node.isObject()) {
                continue;
            }
            String title = first(node, "title", "announceTitle", "name", "content");
            String url = first(node, "url", "linkUrl", "link", "jumpUrl", "href");
            if (title.isEmpty() && url.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", title);
            row.put("url", url);
            rows.add(row);
        }
        return rows;
    }

    private static String first(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static Integer level(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer positive(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.canConvertToInt()) {
            return null;
        }
        int number = value.asInt();
        return number > 0 ? number : null;
    }

    /** COS 配图的缩略图。外站地址原样返回。 */
    public static String thumb(String url) {
        String repaired = repair(url);
        if (repaired.isEmpty() || repaired.contains("imageMogr2") || !repaired.contains("myqcloud.com")) {
            return repaired;
        }
        return repaired + (repaired.contains("?") ? "&" : "?") + "imageMogr2/thumbnail/800x/format/webp";
    }

    private static String absolute(String url, String baseUrl) {
        if (url.isEmpty() || !url.startsWith("/")) {
            return repair(url);
        }
        String base = baseUrl == null || baseUrl.isBlank() ? "https://kzb001.net" : baseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return repair(base + url);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("");
    }
}
