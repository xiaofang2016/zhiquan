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
        if (!document.path("replies").isObject()) {
            document.set("replies", json.createObjectNode());
        }
        for (String name : List.of("likes", "collects", "follows", "blocks", "commentLikes", "replyLikes", "authors", "profiles", "rewards")) {
            if (!document.path(name).isObject()) {
                document.set(name, json.createObjectNode());
            }
        }
        if (!document.path("reports").isArray()) {
            document.set("reports", json.createArrayNode());
        }
        if (!document.path("messages").isArray()) {
            document.set("messages", json.createArrayNode());
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
        post.putNull("badgeStyle");
        post.putNull("vipLevel");
        post.putNull("jxfLevel");
        post.putNull("uedLevel");
        putUser(post, "");
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
        post.put("highlightCommentCount", 0);
        post.putArray("mentionUsers");
        post.put("rewardCount", 0);
        post.put("rewardEnabled", false);
        post.putNull("redPacket");
        posts(board).add(post);
        return post;
    }

    public static ObjectNode addPost(ObjectMapper json, ObjectNode board, String nick, String head, String badge, String title, String content, String userId) {
        ObjectNode post = addPost(json, board, nick, head, badge, title, content);
        putUser(post, userId);
        return post;
    }

    public static ObjectNode addComment(ObjectMapper json, ObjectNode board, String postId, String nick, String head, String badge, String content) {
        return addComment(json, board, postId, nick, head, badge, content, List.of());
    }

    public static ObjectNode addComment(ObjectMapper json, ObjectNode board, String postId, String nick, String head, String badge, String content, List<String> images) {
        ObjectNode comment = json.createObjectNode();
        comment.put("commentId", nextId());
        putUser(comment, "");
        comment.put("nickname", nick);
        comment.put("head", head);
        comment.put("badgeName", badge);
        comment.put("badgeLevel", 0);
        comment.putNull("badgeStyle");
        comment.putNull("vipLevel");
        comment.putNull("jxfLevel");
        comment.putNull("uedLevel");
        comment.put("content", content);
        comment.put("createTime", Instant.now().toString());
        comment.put("cityName", "");
        comment.put("likeCount", 0);
        comment.put("replyCount", 0);
        comment.putArray("replies");
        writeImages(comment, images);
        comments(json, board, postId).add(comment);
        touchReplyTime(board, postId, comment.path("createTime").asText());
        return comment;
    }

    public static ObjectNode addComment(ObjectMapper json, ObjectNode board, String postId, String nick, String head, String badge, String content, List<String> images, String userId) {
        ObjectNode comment = addComment(json, board, postId, nick, head, badge, content, images);
        putUser(comment, userId);
        return comment;
    }

    /**
     * 回复一条已经存在的评论。原站评论不在这份板上时，回复记在 replies 下，读的时候再拼回去。
     */
    public static ObjectNode addReply(ObjectMapper json, ObjectNode board, String postId, String commentId, String replyToUserId, String replyToNickname, String nick, String head, String badge, String content, List<String> images) {
        ensure(json, board);
        ObjectNode reply = json.createObjectNode();
        reply.put("replyId", nextId());
        putUser(reply, "");
        reply.put("nickname", nick);
        reply.put("head", head);
        reply.put("badgeName", badge);
        reply.put("badgeLevel", 0);
        reply.putNull("badgeStyle");
        reply.putNull("vipLevel");
        reply.putNull("jxfLevel");
        reply.putNull("uedLevel");
        reply.put("content", content);
        reply.put("createTime", Instant.now().toString());
        reply.put("cityName", "");
        reply.put("likeCount", 0);
        if (replyToUserId != null && !replyToUserId.isBlank()) {
            reply.put("replyToUserId", replyToUserId);
            reply.put("replyToNickname", replyToNickname == null ? "" : replyToNickname);
        } else {
            reply.put("replyToNickname", "");
        }
        writeImages(reply, images);
        reply.put("postId", postId);
        ObjectNode parent = findComment(board.path("comments").path(postId), commentId);
        if (parent != null) {
            repliesOf(json, parent).add(reply);
            parent.put("replyCount", parent.path("replyCount").asInt(0) + 1);
        } else {
            repliesOf(json, (ObjectNode) board.get("replies"), commentId).add(reply);
        }
        touchReplyTime(board, postId, reply.path("createTime").asText());
        return reply;
    }

    public static ObjectNode addReply(ObjectMapper json, ObjectNode board, String postId, String commentId, String replyToUserId, String replyToNickname, String nick, String head, String badge, String content, List<String> images, String userId) {
        ObjectNode reply = addReply(json, board, postId, commentId, replyToUserId, replyToNickname, nick, head, badge, content, images);
        putUser(reply, userId);
        return reply;
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
        return page(json, feed, page, pageSize, circleId, type, before, board, viewer, "publish");
    }

    /**
     * sort 为 comment 时按最后回复时间，没有回复的用发布时间。publish 按发布时间。
     */
    public static ObjectNode page(ObjectMapper json, ObjectNode feed, int page, int pageSize, Long circleId, String type, String before, JsonNode board, String viewer, String sort) {
        return page(json, feed, page, pageSize, circleId, type, before, board, viewer, sort, null, null);
    }

    /**
     * authorId、topicId 有值时再按作者或话题收窄。仅自己可见的帖子只留给作者。
     */
    public static ObjectNode page(ObjectMapper json, ObjectNode feed, int page, int pageSize, Long circleId, String type, String before, JsonNode board, String viewer, String sort, String authorId, Long topicId) {
        boolean byReply = "comment".equals(sort);
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
            if ("fans".equals(type) && !fanWrote(board, viewer, post)) {
                continue;
            }
            if (blocked(board, viewer, post.path("userId").asText(""))) {
                continue;
            }
            if (!visibleTo(post, viewer)) {
                continue;
            }
            if (authorId != null && !authorId.isBlank() && !authorId.equals(post.path("userId").asText(""))) {
                continue;
            }
            if (topicId != null && !hasTopic(post, topicId)) {
                continue;
            }
            if (beforeMs > 0 && (byReply ? replyTime(post) : time(post)) >= beforeMs) {
                continue;
            }
            picked.add(post);
        }
        if (byReply) {
            picked.sort(Comparator.comparingLong(CommunityBoard::replyTime).reversed());
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
                    payable(copy, board);
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
        if (!visibleTo(object, viewer)) {
            return null;
        }
        ObjectNode copy = object.deepCopy();
        String key = Long.toString(id);
        copy.put("commentCount", copy.path("commentCount").asInt(0) + (board == null ? 0 : board.path("comments").path(key).size()));
        copy.put("likeCount", copy.path("likeCount").asInt(0) + count(board, "likes", key));
        copy.put("collectCount", copy.path("collectCount").asInt(0) + count(board, "collects", key));
        mark(copy, board, viewer);
        payable(copy, board);
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
        return comments(json, upstream, board, postId, page, pageSize, viewer, "newest");
    }

    /**
     * sort 是 newest、oldest 或 like。like 按点赞从高到低，相同再按时间新的在前。
     */
    public static ObjectNode comments(ObjectMapper json, JsonNode upstream, JsonNode board, String postId, int page, int pageSize, String viewer, String sort) {
        List<ObjectNode> ordered = topComments(json, upstream, board, postId, viewer);
        sortNotes(ordered, sort);
        return pageNotes(json, "comments", ordered, page, pageSize, board, viewer);
    }

    /**
     * 亮评是点赞不少于 2 的顶层评论，和原站 highlightCommentCount 对得上。一次给全，按点赞从高到低。
     */
    public static ObjectNode highlights(ObjectMapper json, JsonNode upstream, JsonNode board, String postId, String viewer) {
        List<ObjectNode> ordered = new ArrayList<>();
        for (ObjectNode comment : topComments(json, upstream, board, postId, viewer)) {
            if (comment.path("likeCount").asInt(0) >= 2) {
                ordered.add(comment);
            }
        }
        sortNotes(ordered, "like");
        ObjectNode body = json.createObjectNode();
        ArrayNode comments = body.putArray("comments");
        for (ObjectNode comment : ordered) {
            comments.add(withLocalReplies(json, comment, board, viewer));
        }
        body.put("total", ordered.size());
        return body;
    }

    /**
     * 一条评论下的全部回复。fetched 是原站回复列表里没嵌进评论的那部分。
     */
    public static ObjectNode replies(ObjectMapper json, JsonNode upstream, JsonNode board, String postId, String commentId, JsonNode fetched, int page, int pageSize, String sort, String viewer) {
        ObjectNode parent = findComment(upstream == null ? null : upstream.path("comments"), commentId);
        if (parent == null && board != null) {
            parent = findComment(board.path("comments").path(postId), commentId);
        }
        List<ObjectNode> ordered = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        if (parent != null) {
            appendReplies(ordered, seen, parent.path("replies"), board, viewer);
        }
        appendReplies(ordered, seen, fetched, board, viewer);
        if (board != null) {
            appendReplies(ordered, seen, board.path("replies").path(commentId), board, viewer);
        }
        sortNotes(ordered, sort);
        String author = parent == null ? "" : parent.path("userId").asText("");
        for (ObjectNode reply : ordered) {
            hideDirectReply(reply, author, commentId);
            CommunityLife.paintLike(reply, board, viewer, true);
        }
        return pageNotes(json, "replies", ordered, page, pageSize, null, viewer);
    }

    public static ArrayNode essenceCircles(ObjectMapper json, JsonNode feed) {
        Set<Long> ids = new LinkedHashSet<>();
        if (feed != null) {
            for (JsonNode post : feed.path("posts")) {
                if (!post.path("essence").asBoolean(false)) {
                    continue;
                }
                for (JsonNode id : post.path("circleIds")) {
                    if (id.canConvertToLong()) {
                        ids.add(id.asLong());
                    }
                }
            }
        }
        ArrayNode circles = json.createArrayNode();
        if (feed != null) {
            for (JsonNode circle : feed.path("circles")) {
                if (circle.path("id").canConvertToLong() && ids.contains(circle.path("id").asLong())) {
                    circles.add(circle);
                }
            }
        }
        return circles;
    }

    /**
     * @return 找不到这条评论时为空
     */
    public static String parentName(JsonNode upstream, JsonNode board, String postId, String commentId) {
        ObjectNode parent = findComment(upstream == null ? null : upstream.path("comments"), commentId);
        if (parent == null && board != null) {
            parent = findComment(board.path("comments").path(postId), commentId);
        }
        if (parent == null) {
            return null;
        }
        return parent.path("nickname").asText("");
    }

    public static String parentUserId(JsonNode upstream, JsonNode board, String postId, String commentId) {
        ObjectNode parent = findComment(upstream == null ? null : upstream.path("comments"), commentId);
        if (parent == null && board != null) {
            parent = findComment(board.path("comments").path(postId), commentId);
        }
        if (parent == null || parent.path("userId").isNull() || parent.path("userId").isMissingNode()) {
            return "";
        }
        return parent.path("userId").asText("");
    }

    /**
     * @return 在这条评论和它的回复里找不到这个人时为空
     */
    public static String replyName(JsonNode upstream, JsonNode board, String postId, String commentId, String userId) {
        ObjectNode parent = findComment(upstream == null ? null : upstream.path("comments"), commentId);
        if (parent == null && board != null) {
            parent = findComment(board.path("comments").path(postId), commentId);
        }
        if (parent == null) {
            return null;
        }
        if (userId.equals(parent.path("userId").asText(""))) {
            return parent.path("nickname").asText("");
        }
        for (JsonNode reply : parent.path("replies")) {
            if (userId.equals(reply.path("userId").asText(""))) {
                return reply.path("nickname").asText("");
            }
        }
        if (board != null) {
            for (JsonNode reply : board.path("replies").path(commentId)) {
                if (userId.equals(reply.path("userId").asText(""))) {
                    return reply.path("nickname").asText("");
                }
            }
        }
        return "";
    }

    /** @return 这条回复的作者。找不到时为空 */
    public static String replyOwner(JsonNode upstream, JsonNode board, String postId, String commentId, String replyId) {
        ObjectNode parent = findComment(upstream == null ? null : upstream.path("comments"), commentId);
        if (parent == null && board != null) {
            parent = findComment(board.path("comments").path(postId), commentId);
        }
        if (parent != null) {
            for (JsonNode reply : parent.path("replies")) {
                if (replyId.equals(reply.path("replyId").asText(""))) {
                    return reply.path("userId").asText("");
                }
            }
        }
        if (board != null) {
            for (JsonNode reply : board.path("replies").path(commentId)) {
                if (replyId.equals(reply.path("replyId").asText(""))) {
                    return reply.path("userId").asText("");
                }
            }
        }
        return "";
    }

    public static String postOfComment(JsonNode board, String commentId) {
        if (board == null || !(board.path("comments") instanceof ObjectNode comments)) {
            return null;
        }
        var fields = comments.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (findComment(field.getValue(), commentId) != null) {
                return field.getKey();
            }
        }
        return null;
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
        if (numeric(target)) {
            result.put("userId", Long.parseLong(target));
        } else {
            result.put("userId", target);
        }
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
        if (numeric(target)) {
            result.put("userId", Long.parseLong(target));
        } else {
            result.put("userId", target);
        }
        result.put("blocked", on);
        return result;
    }

    public static ObjectNode report(ObjectMapper json, ObjectNode board, String viewer, String targetType, String targetId, String reason) {
        ensure(json, board);
        String type = targetType == null ? "" : targetType.trim();
        if (!"post".equals(type) && !"comment".equals(type) && !"user".equals(type) && !"reply".equals(type)) {
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

    private static List<ObjectNode> topComments(ObjectMapper json, JsonNode upstream, JsonNode board, String postId, String viewer) {
        ArrayNode gathered = json.createArrayNode();
        Set<Long> seen = new LinkedHashSet<>();
        appendComments(gathered, seen, upstream == null ? null : upstream.path("comments"));
        appendComments(gathered, seen, board == null ? null : board.path("comments").path(postId));
        List<ObjectNode> ordered = new ArrayList<>();
        gathered.forEach(node -> {
            if (node instanceof ObjectNode object && !blocked(board, viewer, object.path("userId").asText(""))) {
                ordered.add(object);
            }
        });
        return ordered;
    }

    private static void sortNotes(List<ObjectNode> ordered, String sort) {
        if ("oldest".equals(sort)) {
            ordered.sort(Comparator.comparingLong(CommunityBoard::stamp));
        } else if ("like".equals(sort)) {
            ordered.sort(Comparator.comparingInt((ObjectNode node) -> node.path("likeCount").asInt(0)).reversed()
                    .thenComparing(Comparator.comparingLong(CommunityBoard::stamp).reversed()));
        } else {
            ordered.sort(Comparator.comparingLong(CommunityBoard::stamp).reversed());
        }
    }

    private static ObjectNode pageNotes(ObjectMapper json, String field, List<ObjectNode> ordered, int page, int pageSize, JsonNode board, String viewer) {
        ObjectNode body = json.createObjectNode();
        int total = ordered.size();
        int from = (page - 1) * pageSize;
        ArrayNode pageItems = body.putArray(field);
        if (from < total) {
            int to = Math.min(from + pageSize, total);
            for (int i = from; i < to; i++) {
                pageItems.add(board == null ? ordered.get(i) : withLocalReplies(json, ordered.get(i), board, viewer));
            }
        }
        body.put("total", total);
        body.put("page", page);
        body.put("pages", total == 0 ? 0 : (total + pageSize - 1) / pageSize);
        return body;
    }

    private static ObjectNode withLocalReplies(ObjectMapper json, ObjectNode comment, JsonNode board, String viewer) {
        ObjectNode copy = comment.deepCopy();
        if (board != null) {
            String commentId = copy.path("commentId").asText("");
            JsonNode extra = board.path("replies").path(commentId);
            if (extra.isArray() && !extra.isEmpty()) {
                ArrayNode replies = repliesOf(json, copy);
                Set<String> seen = new LinkedHashSet<>();
                for (JsonNode reply : replies) {
                    if (reply.path("replyId").canConvertToLong()) {
                        seen.add(reply.path("replyId").asText());
                    }
                }
                for (JsonNode reply : extra) {
                    if (!reply.path("replyId").canConvertToLong() || !seen.add(reply.path("replyId").asText())) {
                        continue;
                    }
                    if (blocked(board, viewer, reply.path("userId").asText(""))) {
                        continue;
                    }
                    replies.add(reply);
                }
            }
        }
        if (copy.path("replies").isArray()) {
            copy.put("replyCount", copy.path("replies").size());
        }
        for (JsonNode reply : copy.path("replies")) {
            if (reply instanceof ObjectNode row) {
                hideDirectReply(row, copy.path("userId").asText(""), copy.path("commentId").asText(""));
                CommunityLife.paintLike(row, board, viewer, true);
            }
        }
        CommunityLife.paintLike(copy, board, viewer, false);
        return copy;
    }

    private static void appendReplies(List<ObjectNode> ordered, Set<Long> seen, JsonNode incoming, JsonNode board, String viewer) {
        if (incoming == null || !incoming.isArray()) {
            return;
        }
        for (JsonNode node : incoming) {
            if (!(node instanceof ObjectNode reply) || !reply.path("replyId").canConvertToLong()) {
                continue;
            }
            if (!seen.add(reply.path("replyId").asLong())) {
                continue;
            }
            if (blocked(board, viewer, reply.path("userId").asText(""))) {
                continue;
            }
            ordered.add(reply);
        }
    }

    private static ObjectNode findComment(JsonNode comments, String commentId) {
        if (comments == null || !comments.isArray() || commentId == null) {
            return null;
        }
        for (JsonNode node : comments) {
            if (node instanceof ObjectNode comment && commentId.equals(comment.path("commentId").asText())) {
                return comment;
            }
        }
        return null;
    }

    private static ArrayNode repliesOf(ObjectMapper json, ObjectNode parent) {
        JsonNode node = parent.get("replies");
        if (node instanceof ArrayNode array) {
            return array;
        }
        return parent.putArray("replies");
    }

    private static ArrayNode repliesOf(ObjectMapper json, ObjectNode parent, String commentId) {
        JsonNode node = parent.get(commentId);
        if (node instanceof ArrayNode array) {
            return array;
        }
        return parent.putArray(commentId);
    }

    private static void writeImages(ObjectNode node, List<String> images) {
        ArrayNode urls = node.putArray("imageUrls");
        if (images == null) {
            return;
        }
        for (String image : images) {
            if (image != null && !image.isBlank()) {
                urls.add(image);
            }
        }
    }

    private static void touchReplyTime(ObjectNode board, String postId, String time) {
        for (JsonNode node : board.path("posts")) {
            if (node instanceof ObjectNode post && postId.equals(post.path("postId").asText())) {
                post.put("lastReplyTime", time);
            }
        }
    }

    private static long replyTime(JsonNode post) {
        long reply = time(post.path("lastReplyTime").asText(""));
        return reply > 0 ? reply : time(post);
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

    public static void markCircle(ObjectNode circle, JsonNode board, String viewer) {
        String id = circle.path("id").asText("");
        int local = 0;
        if (board != null && board.path("follows").isObject() && !id.isEmpty()) {
            var names = board.path("follows").fieldNames();
            while (names.hasNext()) {
                String member = names.next();
                for (JsonNode followed : board.path("follows").path(member).path("circles")) {
                    if (id.equals(followed.asText())) {
                        local++;
                    }
                }
            }
        }
        circle.put("followCount", circle.path("followCount").asInt(0) + local);
        circle.put("followed", followsCircle(board, viewer, id));
    }

    public static void absoluteHeads(JsonNode node, String base) {
        if (node == null || base == null || base.isBlank()) {
            return;
        }
        if (node instanceof ObjectNode object) {
            JsonNode head = object.get("head");
            if (head != null && head.isTextual() && head.asText().startsWith("/")) {
                object.put("head", base + head.asText());
            }
            JsonNode owner = object.get("ownerHead");
            if (owner != null && owner.isTextual() && owner.asText().startsWith("/")) {
                object.put("ownerHead", base + owner.asText());
            }
            object.forEach(child -> absoluteHeads(child, base));
            return;
        }
        if (node instanceof ArrayNode array) {
            array.forEach(child -> absoluteHeads(child, base));
        }
    }

    private static boolean followsCircle(JsonNode board, String viewer, String circleId) {
        if (board == null || viewer == null || viewer.isBlank() || circleId.isEmpty()) {
            return false;
        }
        for (JsonNode id : board.path("follows").path(viewer).path("circles")) {
            if (circleId.equals(id.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 直接回复评论时，被回复的人就是评论作者，不再带回复对象。
     * 带 replyToReplyId 的是在回某一条回复，哪怕对方就是评论作者也保留。
     */
    private static void hideDirectReply(ObjectNode reply, String authorId, String commentId) {
        String nested = reply.path("replyToReplyId").asText("");
        if (!nested.isEmpty() && nested.equals(commentId)) {
            reply.remove("replyToReplyId");
            nested = "";
        }
        if (!nested.isEmpty()) {
            return;
        }
        if (authorId.isEmpty() || !authorId.equals(reply.path("replyToUserId").asText(""))) {
            return;
        }
        reply.remove("replyToUserId");
        reply.remove("replyToNickname");
    }

    private static void putUser(ObjectNode node, String userId) {
        if (userId == null || userId.isBlank()) {
            node.putNull("userId");
        } else {
            node.put("userId", userId);
        }
    }

    private static boolean numeric(String value) {
        return value != null && value.matches("[0-9]{1,18}");
    }

    private static void mark(ObjectNode post, JsonNode board, String viewer) {
        String key = post.path("postId").asText();
        post.put("liked", viewer != null && contains(board, "likes", key, viewer));
        post.put("collected", viewer != null && contains(board, "collects", key, viewer));
        post.put("followed", followsUser(board, viewer, post.path("userId").asText("")));
        if (!post.path("visibility").isTextual()) {
            post.put("visibility", "public");
        }
        CommunityLife.attachRewards(post, board);
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

    /**
     * 只看说球帝本地的关注。原站 authorFollowed 不参与。
     */
    private static boolean followed(JsonNode board, String viewer, JsonNode post) {
        if (followsUser(board, viewer, post.path("userId").asText(""))) {
            return true;
        }
        if (board == null || viewer == null || viewer.isBlank()) {
            return false;
        }
        for (JsonNode circle : board.path("follows").path(viewer).path("circles")) {
            if (circle.canConvertToLong() && inCircle(post, circle.asLong())) {
                return true;
            }
        }
        return false;
    }

    /** 粉丝发的帖：作者关注了当前用户。 */
    private static boolean fanWrote(JsonNode board, String viewer, JsonNode post) {
        if (board == null || viewer == null || viewer.isBlank()) {
            return false;
        }
        String author = post.path("userId").asText("");
        if (author.isEmpty()) {
            return false;
        }
        for (JsonNode id : board.path("follows").path(author).path("users")) {
            if (viewer.equals(id.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean followsUser(JsonNode board, String viewer, String author) {
        if (board == null || viewer == null || viewer.isBlank() || author.isEmpty()) {
            return false;
        }
        for (JsonNode id : board.path("follows").path(viewer).path("users")) {
            if (author.equals(id.asText())) {
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

    private static boolean visibleTo(JsonNode post, String viewer) {
        if (!"self".equals(post.path("visibility").asText("public"))) {
            return true;
        }
        String author = post.path("userId").asText("");
        return viewer != null && !viewer.isBlank() && viewer.equals(author);
    }

    private static boolean hasTopic(JsonNode post, long topicId) {
        for (JsonNode topic : post.path("topics")) {
            if (topic.path("id").canConvertToLong() && topic.path("id").asLong() == topicId) {
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

    /** 没有说球帝作者手机号的帖子不能打赏，开关直接关掉。 */
    static void payable(ObjectNode post, JsonNode board) {
        String id = post.path("postId").asText("");
        if (board == null || board.path("authors").path(id).asText("").isBlank()) {
            post.put("rewardEnabled", false);
        }
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
