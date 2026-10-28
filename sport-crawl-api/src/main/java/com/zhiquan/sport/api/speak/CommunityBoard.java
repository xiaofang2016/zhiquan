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
        post.put("badgeName", badge);
        post.put("likeCount", 0);
        post.put("commentCount", 0);
        post.put("viewCount", 0);
        post.putArray("circleIds");
        post.put("circleName", "");
        post.putArray("images");
        post.putNull("redPacket");
        posts(board).add(post);
        return post;
    }

    public static ObjectNode addComment(ObjectMapper json, ObjectNode board, String postId, String nick, String head, String badge, String content) {
        ObjectNode comment = json.createObjectNode();
        comment.put("commentId", nextId());
        comment.put("nickname", nick);
        comment.put("head", head);
        comment.put("badgeName", badge);
        comment.put("content", content);
        comment.put("createTime", Instant.now().toString());
        comment.put("cityName", "");
        comment.put("likeCount", 0);
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

    public static ObjectNode comments(ObjectMapper json, JsonNode upstream, JsonNode board, String postId) {
        ObjectNode body = json.createObjectNode();
        ArrayNode comments = body.putArray("comments");
        Set<Long> seen = new LinkedHashSet<>();
        appendComments(comments, seen, upstream == null ? null : upstream.path("comments"));
        appendComments(comments, seen, board == null ? null : board.path("comments").path(postId));
        return body;
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
            int extra = board == null ? 0 : board.path("comments").path(Long.toString(id)).size();
            post.put("commentCount", post.path("commentCount").asInt(0) + extra);
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

    private static long stamp(JsonNode post) {
        try {
            return Instant.parse(post.path("createTime").asText("")).toEpochMilli();
        } catch (Exception ex) {
            return 0;
        }
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000L + ThreadLocalRandom.current().nextInt(1000);
    }
}
