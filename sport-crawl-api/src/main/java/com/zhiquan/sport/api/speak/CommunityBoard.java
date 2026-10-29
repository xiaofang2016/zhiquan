package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 本地帖和回复放在抓取不会写的那一份里。读的时候再和原站拼在一起。
 */
public final class CommunityBoard {

    private CommunityBoard() {
    }

    public static ObjectNode ensure(ObjectMapper json, ObjectNode document) {
        if (!document.path("posts").isArray()) {
            document.set("posts", json.createArrayNode());
        }
        if (!document.path("comments").isObject()) {
            document.set("comments", json.createObjectNode());
        }
        for (String name : List.of("likes", "collects", "follows", "blocks")) {
            if (!document.path(name).isObject()) {
                document.set(name, json.createObjectNode());
            }
        }
        if (!document.path("reports").isArray()) {
            document.set("reports", json.createArrayNode());
        }
        return document;
    }

    public static ObjectNode addPost(ObjectMapper json, ObjectNode board, String nick, String head, String badge, String title, String content) {
        ObjectNode post = json.createObjectNode();
        post.put("postId", nextId());
        post.put("title", title);
        post.put("content", content);
        post.put("createTime", Instant.now().toString());
        post.put("nickname", nick);
        post.put("head", head);
        post.put("lastReplyTime", "");
        post.put("badgeName", badge);
        post.put("badgeLevel", 0);
        post.putNull("vipLevel");
        post.putNull("userId");
        post.put("cityName", "");
        post.put("likeCount", 0);
        post.put("commentCount", 0);
        post.put("viewCount", 0);
        post.put("collectCount", 0);
        post.put("essence", false);
        post.putArray("circleIds");
        post.put("circleName", "");
        post.putArray("topics");
        post.putArray("images");
        post.putArray("pictures");
        post.putArray("videos");
        post.putNull("match");
        post.putNull("hotComment");
        post.putNull("redPacket");
        posts(board).add(post);
        return post;
    }

    public static ObjectNode addComment(ObjectMapper json, ObjectNode board, String postId, String nick, String head, String badge, String content) {
        ObjectNode comment = json.createObjectNode();
        comment.put("commentId", nextId());
        comment.putNull("userId");
        comment.put("nickname", nick);
        comment.put("head", head);
        comment.put("badgeName", badge);
        comment.put("badgeLevel", 0);
        comment.putNull("vipLevel");
        comment.put("content", content);
        comment.put("createTime", Instant.now().toString());
        comment.put("cityName", "");
        comment.put("likeCount", 0);
        comment.put("replyCount", 0);
        comment.putArray("replies");
        comments(json, board, postId).add(comment);
        return comment;
    }

    public static ObjectNode feed(ObjectMapper json, JsonNode upstream, JsonNode board) {
        ObjectNode feed = json.createObjectNode();
        ArrayNode posts = feed.putArray("posts");
        ArrayNode circles = feed.putArray("circles");
        Set<Long> seenPosts = new LinkedHashSet<>();
        Set<Long> seenCircles = new LinkedHashSet<>();
        if (upstream != null && upstream.path("circles").isArray()) {
            for (JsonNode circle : upstream.path("circles")) {
                if (circle.path("id").canConvertToLong() && seenCircles.add(circle.path("id").asLong())) {
                    circles.add(circle);
                }
            }
        }
        addPosts(posts, seenPosts, upstream == null ? null : upstream.path("posts"), board);
        addPosts(posts, seenPosts, board == null ? null : board.path("posts"), board);
        List<ObjectNode> ordered = new ArrayList<>();
        posts.forEach(node -> {
            if (node instanceof ObjectNode object) {
                ordered.add(object);
            }
        });
        ordered.sort(Comparator.comparingLong(CommunityBoard::stamp).reversed());
        posts.removeAll();
        ordered.forEach(posts::add);
        return feed;
    }

    /**
     * 按圈子、精华、时间游标切开已经排好的帖子。圈子列表保持全量。
     *
     * @return 当前页。total 是筛完后的帖子数
     */
    public static ObjectNode page(ObjectMapper json, ObjectNode feed, int page, int pageSize, Long circleId, String type, String before) {
        return page(json, feed, page, pageSize, circleId, type, before, null, null);
    }

    /**
     * viewer 是当前登录用户。关注只留这个人关注的作者或圈子，拉黑的作者不出现。
     */
    public static ObjectNode page(ObjectMapper json, ObjectNode feed, int page, int pageSize, Long circleId, String type, String before, JsonNode board, String viewer) {
        long beforeMs = before == null || before.isBlank() ? 0 : time(before);
        List<JsonNode> picked = new ArrayList<>();
        for (JsonNode post : feed.path("posts")) {
            if (circleId != null && !inCircle(post, circleId)) {
                continue;
            }
            if ("essence".equals(type) && !post.path("essence").asBoolean(false)) {
                continue;
            }
            if ("follow".equals(type) && !followed(board, viewer, post)) {
                continue;
            }
            if (blocked(board, viewer, post.path("userId").asText(""))) {
                continue;
            }
            if (beforeMs > 0 && time(post) >= beforeMs) {
                continue;
            }
            picked.add(post);
        }
        ObjectNode body = json.createObjectNode();
        body.set("circles", feed.path("circles").isArray() ? feed.get("circles") : json.createArrayNode());
        ArrayNode posts = body.putArray("posts");
        int total = picked.size();
        int from = (page - 1) * pageSize;
        if (from < total) {
            int to = Math.min(from + pageSize, total);
            for (int i = from; i < to; i++) {
                JsonNode post = picked.get(i);
                if (post instanceof ObjectNode object) {
                    ObjectNode copy = object.deepCopy();
                    mark(copy, board, viewer);
                    PostRedPack.apply(copy, board, viewer);
                    posts.add(copy);
                }
            }
        }
        body.put("total", total);
        body.put("page", page);
        body.put("pages", total == 0 ? 0 : (total + pageSize - 1) / pageSize);
        return body;
    }

    /**
     * 先在当前流里找，再看单独存下的帖子，最后看本机发的。
     *
     * @return 找不到时为空
     */
    public static ObjectNode find(JsonNode feed, JsonNode file, JsonNode board, String postId) {
        return find(feed, file, board, postId, null);
    }

    public static ObjectNode find(JsonNode feed, JsonNode file, JsonNode board, String postId, String viewer) {
        if (postId == null || !postId.matches("[0-9]{1,18}")) {
            return null;
        }
        long id = Long.parseLong(postId);
        JsonNode fromFeed = findIn(feed == null ? null : feed.path("posts"), id);
        JsonNode stored = file != null && file.path("postId").asLong() == id ? file : null;
        JsonNode picked = fromFeed != null ? fromFeed : stored != null ? stored : findIn(board == null ? null : board.path("posts"), id);
        if (!(picked instanceof ObjectNode object)) {
            return null;
        }
        if (blocked(board, viewer, object.path("userId").asText(""))) {
            return null;
        }
        ObjectNode copy = object.deepCopy();
        String key = Long.toString(id);
        copy.put("commentCount", copy.path("commentCount").asInt(0) + (board == null ? 0 : board.path("comments").path(key).size()));
        copy.put("likeCount", copy.path("likeCount").asInt(0) + count(board, "likes", key));
        copy.put("collectCount", copy.path("collectCount").asInt(0) + count(board, "collects", key));
        mark(copy, board, viewer);
        PostRedPack.apply(copy, board, viewer);
        return copy;
    }

    public static ObjectNode comments(ObjectMapper json, JsonNode upstream, JsonNode board, String postId) {
        return comments(json, upstream, board, postId, 1, 50);
    }

    /**
     * @return 当前页的顶层评论。total 不含楼中楼，回复在每条的 replies 里
     */
    public static ObjectNode comments(ObjectMapper json, JsonNode upstream, JsonNode board, String postId, int page, int pageSize) {
        return comments(json, upstream, board, postId, page, pageSize, null);
    }

    public static ObjectNode comments(ObjectMapper json, JsonNode upstream, JsonNode board, String postId, int page, int pageSize, String viewer) {
        ObjectNode body = json.createObjectNode();
        ArrayNode gathered = json.createArrayNode();
        Set<Long> seen = new LinkedHashSet<>();
        appendComments(gathered, seen, upstream == null ? null : upstream.path("comments"));
        appendComments(gathered, seen, board == null ? null : board.path("comments").path(postId));
        List<ObjectNode> ordered = new ArrayList<>();
        gathered.forEach(node -> {
            if (node instanceof ObjectNode object) {
                ordered.add(object);
            }
        });
        ordered.sort(Comparator.comparingLong(CommunityBoard::stamp).reversed());
        ordered.removeIf(comment -> blocked(board, viewer, comment.path("userId").asText("")));
        int total = ordered.size();
        int from = (page - 1) * pageSize;
        ArrayNode comments = body.putArray("comments");
        if (from < total) {
            int to = Math.min(from + pageSize, total);
            for (int i = from; i < to; i++) {
                comments.add(ordered.get(i));
            }
        }
        body.put("total", total);
        body.put("page", page);
        body.put("pages", total == 0 ? 0 : (total + pageSize - 1) / pageSize);
        return body;
    }

    public static ObjectNode like(ObjectMapper json, ObjectNode board, String userId, String postId) {
        ensure(json, board);
        boolean on = toggle(json, (ObjectNode) board.get("likes"), postId, userId);
        ObjectNode result = json.createObjectNode();
        result.put("postId", Long.parseLong(postId));
        result.put("liked", on);
        return result;
    }

    public static ObjectNode collect(ObjectMapper json, ObjectNode board, String userId, String postId) {
        ensure(json, board);
        boolean on = toggle(json, (ObjectNode) board.get("collects"), postId, userId);
        ObjectNode result = json.createObjectNode();
        result.put("postId", Long.parseLong(postId));
        result.put("collected", on);
        return result;
    }

    public static ObjectNode followUser(ObjectMapper json, ObjectNode board, String viewer, String target) {
        ensure(json, board);
        boolean on = toggle(json, bag(json, board, viewer), "users", target);
        ObjectNode result = json.createObjectNode();
        result.put("userId", Long.parseLong(target));
        result.put("followed", on);
        return result;
    }

    public static ObjectNode followCircle(ObjectMapper json, ObjectNode board, String viewer, String circleId) {
        ensure(json, board);
        boolean on = toggle(json, bag(json, board, viewer), "circles", circleId);
        ObjectNode result = json.createObjectNode();
        result.put("circleId", Long.parseLong(circleId));
        result.put("followed", on);
        return result;
    }

    public static ObjectNode block(ObjectMapper json, ObjectNode board, String viewer, String target) {
        ensure(json, board);
        boolean on = toggle(json, (ObjectNode) board.get("blocks"), viewer, target);
        ObjectNode result = json.createObjectNode();
        result.put("userId", Long.parseLong(target));
        result.put("blocked", on);
        return result;
    }

    public static ObjectNode report(ObjectMapper json, ObjectNode board, String viewer, String targetType, String targetId, String reason) {
        ensure(json, board);
        String type = targetType == null ? "" : targetType.trim();
        if (!"post".equals(type) && !"comment".equals(type) && !"user".equals(type)) {
            throw new IllegalArgumentException("举报类型不正确");
        }
        if (targetId == null || !targetId.matches("[0-9]{1,18}")) {
            throw new IllegalArgumentException("举报对象不正确");
        }
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty() || text.length() > 100) {
            throw new IllegalArgumentException("举报原因应为 1 到 100 个字");
        }
        ArrayNode reports = (ArrayNode) board.get("reports");
        for (JsonNode existing : reports) {
            if (viewer.equals(existing.path("userId").asText())
                    && type.equals(existing.path("targetType").asText())
                    && targetId.equals(existing.path("targetId").asText())
                    && existing instanceof ObjectNode row) {
                return row;
            }
        }
        ObjectNode row = json.createObjectNode();
        row.put("userId", viewer);
        row.put("targetType", type);
        row.put("targetId", targetId);
        row.put("reason", text);
        row.put("time", Instant.now().toString());
        reports.add(row);
        while (reports.size() > 500) {
            reports.remove(0);
        }
        return row;
    }

    private static void addPosts(ArrayNode target, Set<Long> seen, JsonNode incoming, JsonNode board) {
        if (incoming == null || !incoming.isArray()) {
            return;
        }
        for (JsonNode node : incoming) {
            if (!(node instanceof ObjectNode post) || !post.path("postId").canConvertToLong()) {
                continue;
            }
            long id = post.path("postId").asLong();
            if (!seen.add(id)) {
                continue;
            }
            String key = Long.toString(id);
            int extra = board == null ? 0 : board.path("comments").path(key).size();
            post.put("commentCount", post.path("commentCount").asInt(0) + extra);
            post.put("likeCount", post.path("likeCount").asInt(0) + count(board, "likes", key));
            post.put("collectCount", post.path("collectCount").asInt(0) + count(board, "collects", key));
            target.add(post);
        }
    }

    private static void appendComments(ArrayNode target, Set<Long> seen, JsonNode incoming) {
        if (incoming == null || !incoming.isArray()) {
            return;
        }
        for (JsonNode node : incoming) {
            if (!node.path("commentId").canConvertToLong() || !seen.add(node.path("commentId").asLong())) {
                continue;
            }
            target.add(node);
        }
    }

    private static ArrayNode posts(ObjectNode board) {
        return (ArrayNode) board.get("posts");
    }

    private static ArrayNode comments(ObjectMapper json, ObjectNode board, String postId) {
        ObjectNode comments = (ObjectNode) board.get("comments");
        JsonNode list = comments.get(postId);
        if (list instanceof ArrayNode array) {
            return array;
        }
        return comments.putArray(postId);
    }

    private static void mark(ObjectNode post, JsonNode board, String viewer) {
        String key = post.path("postId").asText();
        post.put("liked", viewer != null && contains(board, "likes", key, viewer));
        post.put("collected", viewer != null && contains(board, "collects", key, viewer));
    }

    private static ObjectNode bag(ObjectMapper json, ObjectNode board, String viewer) {
        ObjectNode follows = (ObjectNode) board.get("follows");
        JsonNode existing = follows.get(viewer);
        if (existing instanceof ObjectNode bag) {
            return bag;
        }
        ObjectNode bag = follows.putObject(viewer);
        bag.putArray("users");
        bag.putArray("circles");
        return bag;
    }

    private static boolean toggle(ObjectMapper json, ObjectNode parent, String key, String member) {
        ArrayNode list = array(json, parent, key);
        for (int i = 0; i < list.size(); i++) {
            if (member.equals(list.get(i).asText())) {
                list.remove(i);
                return false;
            }
        }
        list.add(member);
        return true;
    }

    private static ArrayNode array(ObjectMapper json, ObjectNode parent, String key) {
        JsonNode node = parent.get(key);
        if (node instanceof ArrayNode array) {
            return array;
        }
        return parent.putArray(key);
    }

    private static int count(JsonNode board, String group, String key) {
        if (board == null) {
            return 0;
        }
        JsonNode list = board.path(group).path(key);
        return list.isArray() ? list.size() : 0;
    }

    private static boolean contains(JsonNode board, String group, String key, String member) {
        if (board == null) {
            return false;
        }
        for (JsonNode item : board.path(group).path(key)) {
            if (member.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean followed(JsonNode board, String viewer, JsonNode post) {
        if (board == null || viewer == null || viewer.isBlank()) {
            return false;
        }
        JsonNode bag = board.path("follows").path(viewer);
        String author = post.path("userId").asText("");
        for (JsonNode id : bag.path("users")) {
            if (author.equals(id.asText())) {
                return true;
            }
        }
        for (JsonNode circle : bag.path("circles")) {
            if (circle.canConvertToLong() && inCircle(post, circle.asLong())) {
                return true;
            }
        }
        return false;
    }

    private static boolean blocked(JsonNode board, String viewer, String author) {
        if (board == null || viewer == null || viewer.isBlank() || author.isEmpty()) {
            return false;
        }
        for (JsonNode id : board.path("blocks").path(viewer)) {
            if (author.equals(id.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inCircle(JsonNode post, long circleId) {
        for (JsonNode id : post.path("circleIds")) {
            if (id.canConvertToLong() && id.asLong() == circleId) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode findIn(JsonNode posts, long postId) {
        if (posts == null || !posts.isArray()) {
            return null;
        }
        for (JsonNode post : posts) {
            if (post.path("postId").asLong() == postId) {
                return post;
            }
        }
        return null;
    }

    private static long time(JsonNode post) {
        return time(post.path("createTime").asText(""));
    }

    private static long time(String value) {
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception ex) {
            return 0;
        }
    }

    private static long stamp(JsonNode post) {
        return time(post);
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000L + ThreadLocalRandom.current().nextInt(1000);
    }
}
