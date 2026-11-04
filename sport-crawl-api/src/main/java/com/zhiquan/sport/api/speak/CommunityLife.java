package com.zhiquan.sport.api.speak;

import com.zhiquan.sport.api.user.UserAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 说球帝自己的评论、帖子、打赏、主页和通知。原站作者的打赏不入账。
 */
public final class CommunityLife {

    private CommunityLife() {
    }

    public static void paintLike(ObjectNode note, JsonNode board, String viewer, boolean reply) {
        if (note == null) {
            return;
        }
        String id = note.path(reply ? "replyId" : "commentId").asText("");
        String bag = reply ? "replyLikes" : "commentLikes";
        if (!id.isEmpty() && board != null) {
            note.put("likeCount", note.path("likeCount").asInt(0) + size(board, bag, id));
        }
        note.put("liked", viewer != null && !id.isEmpty() && member(board, bag, id, viewer));
    }

    public static void attachRewards(ObjectNode post, JsonNode board) {
        String key = post.path("postId").asText("");
        JsonNode rewards = board == null ? null : board.path("rewards").path(key);
        int extra = rewards != null && rewards.isArray() ? rewards.size() : 0;
        post.put("rewardCount", post.path("rewardCount").asInt(0) + extra);
        ArrayNode shown = post.putArray("rewardUsers");
        if (extra == 0) {
            return;
        }
        int from = Math.max(0, rewards.size() - 8);
        for (int i = rewards.size() - 1; i >= from; i--) {
            JsonNode row = rewards.get(i);
            ObjectNode person = shown.addObject();
            person.put("userId", row.path("userId").asText(""));
            person.put("nickname", row.path("nickname").asText(""));
            person.put("head", row.path("head").asText(""));
        }
    }

    public static ObjectNode publish(ObjectMapper json, ObjectNode board, String nick, String head, String userId, String phone,
                                      String title, String content, List<Long> circleIds, List<String> pictures, List<String> videos,
                                      List<Long> topicIds, List<String> mentionUserIds, Long matchId, String matchType, String cityName) {
        CommunityBoard.ensure(json, board);
        ObjectNode post = CommunityBoard.addPost(json, board, nick, head, "", title, content, userId);
        if (phone != null && !phone.isBlank()) {
            ((ObjectNode) board.get("authors")).put(post.path("postId").asText(), phone);
        }
        writePost(post, circleIds, pictures, videos, topicIds, mentionUserIds, matchId, matchType);
        city(post, cityName == null || cityName.isBlank() ? null : UserAuthService.city(cityName));
        post.put("visibility", "public");
        post.put("rewardEnabled", false);
        remember(json, board, userId, nick, head);
        return post;
    }

    public static ObjectNode edit(ObjectMapper json, ObjectNode board, String viewer, String postId, String title, String content,
                                   List<Long> circleIds, List<String> pictures, List<String> videos, List<Long> topicIds,
                                   List<String> mentionUserIds, Long matchId, String matchType) {
        ObjectNode post = ownPost(board, viewer, postId);
        if (title != null) {
            post.put("title", Voice.title(title));
        }
        if (content != null) {
            post.put("content", Voice.post(content));
        }
        writePost(post, circleIds, pictures, videos, topicIds, mentionUserIds, matchId, matchType);
        return post;
    }

    public static ObjectNode removePost(ObjectNode board, String viewer, String postId) {
        ownPost(board, viewer, postId);
        ArrayNode posts = (ArrayNode) board.get("posts");
        for (int i = 0; i < posts.size(); i++) {
            if (postId.equals(posts.get(i).path("postId").asText())) {
                posts.remove(i);
                break;
            }
        }
        ObjectNode result = board.objectNode();
        result.put("postId", Long.parseLong(postId));
        result.put("removed", true);
        return result;
    }

    public static ObjectNode visibility(ObjectNode board, String viewer, String postId, String visibility) {
        ObjectNode post = ownPost(board, viewer, postId);
        String value = visibility == null ? "" : visibility.trim();
        if (!"public".equals(value) && !"self".equals(value)) {
            throw new IllegalArgumentException("查看权限不正确");
        }
        post.put("visibility", value);
        return post;
    }

    public static ObjectNode rewardSwitch(ObjectNode board, String viewer, String postId) {
        ObjectNode post = ownPost(board, viewer, postId);
        boolean on = !post.path("rewardEnabled").asBoolean(false);
        post.put("rewardEnabled", on);
        return post;
    }

    public static String rewardPhone(ObjectNode board, String postId) {
        return board.path("authors").path(postId).asText("");
    }

    public static ObjectNode commitReward(ObjectMapper json, ObjectNode board, String postId, String userId, String nick, String head, long cents) {
        CommunityBoard.ensure(json, board);
        ArrayNode rewards = rewards(json, board, postId);
        ObjectNode row = json.createObjectNode();
        row.put("userId", userId);
        row.put("nickname", nick);
        row.put("head", head);
        row.put("amount", String.format(java.util.Locale.ROOT, "%d.%02d", cents / 100, cents % 100));
        row.put("createTime", Instant.now().toString());
        rewards.add(row);
        return row;
    }

    public static ObjectNode rewards(ObjectMapper json, ObjectNode board, String postId, int page, int pageSize) {
        JsonNode all = board.path("rewards").path(postId);
        List<ObjectNode> ordered = new ArrayList<>();
        if (all.isArray()) {
            for (int i = all.size() - 1; i >= 0; i--) {
                if (all.get(i) instanceof ObjectNode row) {
                    ordered.add(row);
                }
            }
        }
        return slice(json, "rewards", ordered, page, pageSize);
    }

    public static ObjectNode likeNote(ObjectMapper json, ObjectNode board, String viewer, String nick, String head, String id, boolean reply, String authorId, String postId, String commentId) {
        return likeNote(json, board, viewer, nick, head, id, reply, authorId, postId, commentId, 0);
    }

    /** base 是原站已经有的赞，返回值不会小于它。 */
    public static ObjectNode likeNote(ObjectMapper json, ObjectNode board, String viewer, String nick, String head, String id, boolean reply, String authorId, String postId, String commentId, int base) {
        CommunityBoard.ensure(json, board);
        String bag = reply ? "replyLikes" : "commentLikes";
        boolean on = toggle(json, (ObjectNode) board.get(bag), id, viewer);
        if (on) {
            note(json, board, authorId, viewer, nick, head, reply ? "like" : "like", postId, commentId, reply ? "赞了你的回复" : "赞了你的评论");
        }
        ObjectNode result = json.createObjectNode();
        if (reply) {
            result.put("replyId", Long.parseLong(id));
        } else {
            result.put("commentId", Long.parseLong(id));
        }
        result.put("liked", on);
        result.put("likeCount", Math.max(0, base) + size(board, bag, id));
        return result;
    }

    public static ObjectNode removeComment(ObjectMapper json, ObjectNode board, String viewer, String postId, String commentId) {
        CommunityBoard.ensure(json, board);
        ArrayNode comments = comments(board, postId);
        for (int i = 0; i < comments.size(); i++) {
            JsonNode comment = comments.get(i);
            if (!commentId.equals(comment.path("commentId").asText())) {
                continue;
            }
            if (!viewer.equals(comment.path("userId").asText(""))) {
                throw new IllegalArgumentException("只能删除自己的评论");
            }
            comments.remove(i);
            ObjectNode result = json.createObjectNode();
            result.put("commentId", Long.parseLong(commentId));
            result.put("removed", true);
            return result;
        }
        throw new IllegalArgumentException("只能删除自己的评论");
    }

    public static ObjectNode removeReply(ObjectMapper json, ObjectNode board, String viewer, String replyId) {
        CommunityBoard.ensure(json, board);
        if (!walk(board.path("comments"), viewer, replyId) && !walk(board.path("replies"), viewer, replyId)) {
            throw new IllegalArgumentException("只能删除自己的回复");
        }
        ObjectNode result = json.createObjectNode();
        result.put("replyId", Long.parseLong(replyId));
        result.put("removed", true);
        return result;
    }

    public static void city(ObjectNode note, String cityName) {
        if (note == null || cityName == null || cityName.isBlank()) {
            return;
        }
        String city = cityName.trim();
        if (city.length() > 20) {
            throw new IllegalArgumentException("城市名称过长");
        }
        note.put("cityName", city);
    }

    public static void note(ObjectMapper json, ObjectNode board, String toUserId, String fromUserId, String nick, String head, String type, String postId, String commentId, String summary) {
        if (toUserId == null || !toUserId.matches("[0-9a-fA-F]{32}") || toUserId.equalsIgnoreCase(fromUserId)) {
            return;
        }
        CommunityBoard.ensure(json, board);
        ObjectNode row = json.createObjectNode();
        row.put("id", Long.toString(System.currentTimeMillis()) + ThreadLocalRandom.current().nextInt(1000));
        row.put("type", type);
        row.put("toUserId", toUserId);
        row.put("userId", fromUserId);
        row.put("nickname", nick == null ? "" : nick);
        row.put("head", head == null ? "" : head);
        row.put("summary", summary == null ? "" : summary);
        row.put("postId", postId == null ? "" : postId);
        row.put("commentId", commentId == null ? "" : commentId);
        row.put("createTime", Instant.now().toString());
        row.put("read", false);
        ArrayNode messages = (ArrayNode) board.get("messages");
        messages.add(row);
        while (messages.size() > 1000) {
            messages.remove(0);
        }
    }

    public static ObjectNode messages(ObjectMapper json, ObjectNode board, String viewer, String type, int page, int pageSize) {
        List<ObjectNode> ordered = new ArrayList<>();
        JsonNode all = board.path("messages");
        if (all.isArray()) {
            for (int i = all.size() - 1; i >= 0; i--) {
                JsonNode row = all.get(i);
                if (!(row instanceof ObjectNode message) || !viewer.equals(message.path("toUserId").asText())) {
                    continue;
                }
                if (type != null && !type.isBlank() && !type.equals(message.path("type").asText())) {
                    continue;
                }
                ordered.add(message);
            }
        }
        return slice(json, "messages", ordered, page, pageSize);
    }

    public static ObjectNode unread(ObjectMapper json, ObjectNode board, String viewer) {
        int comment = 0;
        int reply = 0;
        int like = 0;
        int follow = 0;
        int system = 0;
        for (JsonNode row : board.path("messages")) {
            if (!viewer.equals(row.path("toUserId").asText()) || row.path("read").asBoolean(false)) {
                continue;
            }
            switch (row.path("type").asText("")) {
                case "comment" -> comment++;
                case "reply" -> reply++;
                case "like" -> like++;
                case "follow" -> follow++;
                default -> system++;
            }
        }
        ObjectNode body = json.createObjectNode();
        body.put("comment", comment);
        body.put("reply", reply);
        body.put("like", like);
        body.put("follow", follow);
        body.put("system", system);
        body.put("total", comment + reply + like + follow + system);
        return body;
    }

    public static ObjectNode readMessages(ObjectMapper json, ObjectNode board, String viewer, List<String> ids) {
        Set<String> wanted = ids == null ? Set.of() : new LinkedHashSet<>(ids);
        int changed = 0;
        for (JsonNode row : board.path("messages")) {
            if (!(row instanceof ObjectNode message) || !viewer.equals(message.path("toUserId").asText())) {
                continue;
            }
            if (!wanted.isEmpty() && !wanted.contains(message.path("id").asText())) {
                continue;
            }
            if (!message.path("read").asBoolean(false)) {
                message.put("read", true);
                changed++;
            }
        }
        ObjectNode result = json.createObjectNode();
        result.put("read", changed);
        return result;
    }

    public static ObjectNode profile(ObjectMapper json, JsonNode feed, ObjectNode board, String userId, String viewer) {
        ObjectNode body = json.createObjectNode();
        body.put("userId", userId);
        Face face = new Face();
        if (feed != null) {
            for (JsonNode post : feed.path("posts")) {
                consider(face, post, userId, board, true);
            }
        }
        if (board != null) {
            for (JsonNode post : board.path("posts")) {
                consider(face, post, userId, board, true);
            }
            var comments = board.path("comments").fields();
            while (comments.hasNext()) {
                for (JsonNode comment : comments.next().getValue()) {
                    consider(face, comment, userId, board, false);
                    for (JsonNode reply : comment.path("replies")) {
                        consider(face, reply, userId, board, false);
                    }
                }
            }
            var extras = board.path("replies").fields();
            while (extras.hasNext()) {
                for (JsonNode reply : extras.next().getValue()) {
                    consider(face, reply, userId, board, false);
                }
            }
        }
        JsonNode stored = board == null ? null : board.path("profiles").path(userId);
        String nick = face.nick;
        String head = face.head;
        if (nick.isEmpty() && stored != null) {
            nick = stored.path("nickname").asText("");
        }
        if (head.isEmpty() && stored != null) {
            head = stored.path("head").asText("");
        }
        body.put("nickname", nick.isEmpty() ? "用户" : nick);
        body.put("head", head);
        body.put("bg", stored == null ? "" : stored.path("bg").asText(""));
        body.put("intro", stored == null ? "" : stored.path("intro").asText(""));
        body.put("cityName", stored == null ? "" : stored.path("cityName").asText(""));
        if (face.badgeLevel == null) {
            body.putNull("badgeLevel");
        } else {
            body.put("badgeLevel", face.badgeLevel);
        }
        body.put("badgeName", face.badgeName);
        if (face.badgeStyle == null) {
            body.putNull("badgeStyle");
        } else {
            body.put("badgeStyle", face.badgeStyle);
        }
        if (face.jxf == null) {
            body.putNull("jxfLevel");
        } else {
            body.put("jxfLevel", face.jxf);
        }
        if (face.ued == null) {
            body.putNull("uedLevel");
        } else {
            body.put("uedLevel", face.ued);
        }
        body.put("followCount", board == null ? 0 : board.path("follows").path(userId).path("users").size());
        body.put("fansCount", fansOf(board, userId).size());
        body.put("likeCount", face.likes);
        body.put("followed", follows(board, viewer, userId));
        body.put("blocked", blocks(board, viewer, userId));
        return body;
    }

    public static void remember(ObjectMapper json, ObjectNode board, String userId, String nick, String head) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        CommunityBoard.ensure(json, board);
        ObjectNode profiles = (ObjectNode) board.get("profiles");
        ObjectNode row = profiles.path(userId).isObject() ? (ObjectNode) profiles.get(userId) : profiles.putObject(userId);
        if (nick != null && !nick.isBlank()) {
            row.put("nickname", nick);
        }
        if (head != null && !head.isBlank()) {
            row.put("head", head);
        }
    }

    /** 原站记在这条评论或回复上的赞，不含说球帝后来点的。 */
    public static int originalLikes(JsonNode upstream, JsonNode board, String id, boolean reply) {
        JsonNode found = locate(upstream, board, id, reply);
        return found == null ? 0 : found.path("likeCount").asInt(0);
    }

    private static JsonNode locate(JsonNode upstream, JsonNode board, String id, boolean reply) {
        JsonNode found = scan(upstream == null ? null : upstream.path("comments"), id, reply);
        if (found != null) {
            return found;
        }
        if (board == null) {
            return null;
        }
        var comments = board.path("comments").fields();
        while (comments.hasNext()) {
            found = scan(comments.next().getValue(), id, reply);
            if (found != null) {
                return found;
            }
        }
        if (!reply) {
            return null;
        }
        var extras = board.path("replies").fields();
        while (extras.hasNext()) {
            found = scan(extras.next().getValue(), id, true);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static JsonNode scan(JsonNode list, String id, boolean reply) {
        if (list == null || !list.isArray()) {
            return null;
        }
        for (JsonNode node : list) {
            if (reply) {
                if (id.equals(node.path("replyId").asText(""))) {
                    return node;
                }
                JsonNode nested = scan(node.path("replies"), id, true);
                if (nested != null) {
                    return nested;
                }
            } else if (id.equals(node.path("commentId").asText(""))) {
                return node;
            }
        }
        return null;
    }

    private static void consider(Face face, JsonNode node, String userId, JsonNode board, boolean post) {
        if (node == null || !userId.equals(node.path("userId").asText(""))) {
            return;
        }
        String time = node.path("createTime").asText("");
        if (time.compareTo(face.time) >= 0) {
            String nick = node.path("nickname").asText("");
            if (!nick.isEmpty()) {
                face.nick = nick;
            }
            String head = node.path("head").asText("");
            if (!head.isEmpty()) {
                face.head = head;
            }
            boolean named = !node.path("badgeName").asText("").isEmpty();
            boolean leveled = node.path("badgeLevel").isNumber() && node.path("badgeLevel").asInt() > 0;
            if (named || leveled) {
                face.badgeName = node.path("badgeName").asText("");
                face.badgeLevel = node.path("badgeLevel").isNumber() ? node.path("badgeLevel").asInt() : null;
                face.badgeStyle = node.path("badgeStyle").isNumber() ? node.path("badgeStyle").asInt() : null;
                face.jxf = node.path("jxfLevel").isNumber() ? node.path("jxfLevel").asInt() : null;
                face.ued = node.path("uedLevel").isNumber() ? node.path("uedLevel").asInt() : null;
            }
            face.time = time;
        }
        if (!post) {
            return;
        }
        String postId = node.path("postId").asText("");
        if (face.counted.add(postId)) {
            face.likes += node.path("likeCount").asInt(0);
            if (board != null) {
                face.likes += size(board, "likes", postId);
            }
        }
    }

    private static final class Face {
        private String nick = "";
        private String head = "";
        private String time = "";
        private String badgeName = "";
        private Integer badgeLevel;
        private Integer badgeStyle;
        private Integer jxf;
        private Integer ued;
        private int likes;
        private final Set<String> counted = new LinkedHashSet<>();
    }

    public static ObjectNode people(ObjectMapper json, JsonNode feed, ObjectNode board, String userId, String viewer, boolean fans, int page, int pageSize) {
        List<String> ids = fans ? fansOf(board, userId) : followedUsers(board, userId);
        List<ObjectNode> ordered = new ArrayList<>();
        for (String id : ids) {
            ObjectNode person = profile(json, feed, board, id, viewer);
            ordered.add(person);
        }
        return slice(json, "users", ordered, page, pageSize);
    }

    public static ObjectNode saveProfile(ObjectMapper json, ObjectNode board, String userId, String intro, String bg, String cityName) {
        CommunityBoard.ensure(json, board);
        ObjectNode profiles = (ObjectNode) board.get("profiles");
        ObjectNode row = profiles.path(userId).isObject() ? (ObjectNode) profiles.get(userId) : profiles.putObject(userId);
        if (intro != null) {
            String text = intro.trim();
            if (text.length() > 100) {
                throw new IllegalArgumentException("简介应为 100 个字以内");
            }
            row.put("intro", text);
        }
        if (bg != null) {
            row.put("bg", bg);
        }
        if (cityName != null) {
            row.put("cityName", UserAuthService.city(cityName));
        }
        return row;
    }

    public static ObjectNode myPosts(ObjectMapper json, JsonNode feed, ObjectNode board, String viewer, String group, int page, int pageSize) {
        List<ObjectNode> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectMine(ordered, seen, feed == null ? null : feed.path("posts"), board, viewer, group);
        collectMine(ordered, seen, board.path("posts"), board, viewer, group);
        return slice(json, "posts", ordered, page, pageSize);
    }

    public static void clearMine(ObjectNode board, String viewer, String group) {
        JsonNode parent = board.path(group);
        if (!(parent instanceof ObjectNode object)) {
            return;
        }
        var names = object.fieldNames();
        List<String> keys = new ArrayList<>();
        names.forEachRemaining(keys::add);
        for (String key : keys) {
            JsonNode list = object.get(key);
            if (!(list instanceof ArrayNode array)) {
                continue;
            }
            for (int i = array.size() - 1; i >= 0; i--) {
                if (viewer.equals(array.get(i).asText())) {
                    array.remove(i);
                }
            }
        }
    }

    public static ObjectNode myComments(ObjectMapper json, JsonNode feed, JsonNode index, ObjectNode board, String viewer, int page, int pageSize) {
        List<ObjectNode> ordered = new ArrayList<>();
        var posts = board.path("comments").fields();
        while (posts.hasNext()) {
            var field = posts.next();
            for (JsonNode comment : field.getValue()) {
                if (viewer.equals(comment.path("userId").asText()) && comment instanceof ObjectNode row) {
                    ordered.add(withPost(json, row, field.getKey(), titleOf(feed, board, field.getKey()), false));
                }
                for (JsonNode reply : comment.path("replies")) {
                    if (viewer.equals(reply.path("userId").asText()) && reply instanceof ObjectNode replyRow) {
                        ordered.add(withPost(json, replyRow, field.getKey(), titleOf(feed, board, field.getKey()), true));
                    }
                }
            }
        }
        var extras = board.path("replies").fields();
        while (extras.hasNext()) {
            var field = extras.next();
            for (JsonNode reply : field.getValue()) {
                if (viewer.equals(reply.path("userId").asText()) && reply instanceof ObjectNode row) {
                    String postId = row.path("postId").asText("");
                    if (postId.isEmpty() && index != null) {
                        postId = index.path(field.getKey()).asText("");
                    }
                    ordered.add(withPost(json, row, postId, titleOf(feed, board, postId), true));
                }
            }
        }
        ordered.sort((left, right) -> right.path("createTime").asText("").compareTo(left.path("createTime").asText("")));
        return slice(json, "comments", ordered, page, pageSize);
    }

    public static ArrayNode myCircles(ObjectMapper json, JsonNode feed, ObjectNode board, String viewer) {
        ArrayNode rows = json.createArrayNode();
        for (JsonNode id : board.path("follows").path(viewer).path("circles")) {
            if (feed == null) {
                continue;
            }
            for (JsonNode circle : feed.path("circles")) {
                if (id.asText().equals(circle.path("id").asText()) && circle instanceof ObjectNode object) {
                    ObjectNode copy = object.deepCopy();
                    CommunityBoard.markCircle(copy, board, viewer);
                    rows.add(copy);
                }
            }
        }
        return rows;
    }

    public static ObjectNode myBlocks(ObjectMapper json, JsonNode feed, ObjectNode board, String viewer, int page, int pageSize) {
        List<ObjectNode> ordered = new ArrayList<>();
        for (JsonNode id : board.path("blocks").path(viewer)) {
            ordered.add(profile(json, feed, board, id.asText(), viewer));
        }
        return slice(json, "users", ordered, page, pageSize);
    }

    public static ObjectNode topic(ObjectMapper json, JsonNode feed, long topicId) {
        String name = "";
        String cover = "";
        int posts = 0;
        if (feed != null) {
            for (JsonNode circle : feed.path("circles")) {
                for (JsonNode topic : circle.path("topics")) {
                    if (topic.path("id").asLong() == topicId) {
                        name = topic.path("name").asText(name);
                        cover = topic.path("cover").asText(cover);
                    }
                }
            }
            for (JsonNode post : feed.path("posts")) {
                if (hasTopic(post, topicId)) {
                    posts++;
                }
            }
        }
        if (name.isEmpty() && posts == 0) {
            return null;
        }
        ObjectNode body = json.createObjectNode();
        body.put("id", topicId);
        body.put("name", name);
        body.put("cover", cover);
        body.put("intro", "");
        body.put("postCount", posts);
        return body;
    }

    public static ObjectNode announce(JsonNode feed, long announceId) {
        if (feed == null) {
            return null;
        }
        for (JsonNode circle : feed.path("circles")) {
            for (JsonNode item : circle.path("announces")) {
                if (item.path("id").asLong() != announceId) {
                    continue;
                }
                if (!(item instanceof ObjectNode row)) {
                    return null;
                }
                ObjectNode copy = row.deepCopy();
                if (!copy.path("content").isTextual()) {
                    copy.put("content", "");
                }
                return copy;
            }
        }
        return null;
    }

    public static String author(ObjectNode board, String id, boolean reply) {
        String field = reply ? "replyId" : "commentId";
        String found = authorIn(board.path("comments"), field, id);
        if (!found.isEmpty() || !reply) {
            return found;
        }
        return authorIn(board.path("replies"), "replyId", id);
    }

    private static String authorIn(JsonNode node, String field, String id) {
        if (node == null) {
            return "";
        }
        if (node.isObject() && id.equals(node.path(field).asText(""))) {
            return node.path("userId").asText("");
        }
        if (node.isArray() || node.isObject()) {
            for (JsonNode child : node) {
                String found = authorIn(child, field, id);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    public static long cents(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!text.matches("\\d+(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("打赏金额不正确");
        }
        String[] parts = text.split("\\.");
        long yuan = Long.parseLong(parts[0]);
        int frac = 0;
        if (parts.length == 2) {
            frac = Integer.parseInt(parts[1].length() == 1 ? parts[1] + "0" : parts[1]);
        }
        long cents = yuan * 100 + frac;
        if (cents < 1 || cents > 20_000) {
            throw new IllegalArgumentException("打赏金额为 0.01 到 200 元");
        }
        return cents;
    }

    private static void writePost(ObjectNode post, List<Long> circleIds, List<String> pictures, List<String> videos,
                                  List<Long> topicIds, List<String> mentionUserIds, Long matchId, String matchType) {
        if (circleIds != null) {
            ArrayNode ids = post.putArray("circleIds");
            String name = "";
            for (Long id : circleIds) {
                if (id == null || id <= 0) {
                    continue;
                }
                ids.add(id);
            }
            post.put("circleName", name);
        }
        if (pictures != null) {
            ArrayNode images = post.putArray("images");
            ArrayNode rows = post.putArray("pictures");
            for (String picture : pictures) {
                if (picture == null || !picture.startsWith("http")) {
                    continue;
                }
                images.add(picture);
                ObjectNode row = rows.addObject();
                row.put("url", picture);
                row.put("thumb", picture);
                row.putNull("w");
                row.putNull("h");
            }
        }
        if (videos != null) {
            ArrayNode rows = post.putArray("videos");
            for (String video : videos) {
                if (video == null || !video.startsWith("http")) {
                    continue;
                }
                ObjectNode row = rows.addObject();
                row.put("url", video);
                row.put("cover", "");
                row.putNull("w");
                row.putNull("h");
                row.putNull("duration");
            }
        }
        if (topicIds != null) {
            ArrayNode topics = post.putArray("topics");
            for (Long id : topicIds) {
                if (id == null || id <= 0) {
                    continue;
                }
                ObjectNode topic = topics.addObject();
                topic.put("id", id);
                topic.put("name", "");
            }
        }
        if (mentionUserIds != null) {
            ArrayNode mentions = post.putArray("mentionUsers");
            for (String id : mentionUserIds) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                ObjectNode person = mentions.addObject();
                person.put("userId", id);
                person.put("nickname", "");
            }
        }
        if (matchId != null && matchId > 0) {
            ObjectNode match = post.putObject("match");
            match.put("id", matchId);
            match.put("type", matchType == null ? "" : matchType);
            match.put("home", "");
            match.put("away", "");
            match.put("homeLogo", "");
            match.put("awayLogo", "");
            match.putNull("score");
            match.putNull("status");
        }
    }

    private static ObjectNode ownPost(ObjectNode board, String viewer, String postId) {
        ObjectNode post = findOwnOrAny(board, postId);
        if (post == null || !viewer.equals(post.path("userId").asText(""))) {
            throw new IllegalArgumentException("只能修改自己的帖子");
        }
        return post;
    }

    private static ObjectNode findOwnOrAny(ObjectNode board, String postId) {
        for (JsonNode node : board.path("posts")) {
            if (postId.equals(node.path("postId").asText()) && node instanceof ObjectNode post) {
                return post;
            }
        }
        return null;
    }

    private static boolean walk(JsonNode node, String viewer, String replyId) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isArray()) {
            if (!node.isEmpty() && node.get(0).path("replyId").canConvertToLong()) {
                return takeReply(node, viewer, replyId);
            }
            for (JsonNode child : node) {
                if (walk(child, viewer, replyId)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            if (node.path("replies").isArray() && takeReply(node.path("replies"), viewer, replyId)) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (walk(fields.next().getValue(), viewer, replyId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean takeReply(JsonNode list, String viewer, String replyId) {
        if (!(list instanceof ArrayNode array)) {
            return false;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonNode reply = array.get(i);
            if (!replyId.equals(reply.path("replyId").asText())) {
                continue;
            }
            if (!viewer.equals(reply.path("userId").asText(""))) {
                throw new IllegalArgumentException("只能删除自己的回复");
            }
            array.remove(i);
            return true;
        }
        return false;
    }

    private static ArrayNode comments(ObjectNode board, String postId) {
        JsonNode list = board.path("comments").path(postId);
        if (list instanceof ArrayNode array) {
            return array;
        }
        return ((ObjectNode) board.get("comments")).putArray(postId);
    }

    private static ArrayNode rewards(ObjectMapper json, ObjectNode board, String postId) {
        ObjectNode rewards = (ObjectNode) board.get("rewards");
        JsonNode list = rewards.get(postId);
        if (list instanceof ArrayNode array) {
            return array;
        }
        return rewards.putArray(postId);
    }

    private static boolean toggle(ObjectMapper json, ObjectNode parent, String key, String member) {
        ArrayNode list = parent.path(key).isArray() ? (ArrayNode) parent.get(key) : parent.putArray(key);
        for (int i = 0; i < list.size(); i++) {
            if (member.equals(list.get(i).asText())) {
                list.remove(i);
                return false;
            }
        }
        list.add(member);
        return true;
    }

    private static int size(JsonNode board, String group, String key) {
        JsonNode list = board.path(group).path(key);
        return list.isArray() ? list.size() : 0;
    }

    private static boolean member(JsonNode board, String group, String key, String userId) {
        if (board == null) {
            return false;
        }
        for (JsonNode item : board.path(group).path(key)) {
            if (userId.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean follows(JsonNode board, String viewer, String userId) {
        if (viewer == null || viewer.isBlank()) {
            return false;
        }
        for (JsonNode id : board.path("follows").path(viewer).path("users")) {
            if (userId.equals(id.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean blocks(JsonNode board, String viewer, String userId) {
        if (viewer == null || viewer.isBlank()) {
            return false;
        }
        for (JsonNode id : board.path("blocks").path(viewer)) {
            if (userId.equals(id.asText())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> followedUsers(JsonNode board, String userId) {
        List<String> ids = new ArrayList<>();
        for (JsonNode id : board.path("follows").path(userId).path("users")) {
            ids.add(id.asText());
        }
        return ids;
    }

    private static List<String> fansOf(JsonNode board, String userId) {
        List<String> ids = new ArrayList<>();
        var names = board.path("follows").fieldNames();
        while (names.hasNext()) {
            String member = names.next();
            for (JsonNode id : board.path("follows").path(member).path("users")) {
                if (userId.equals(id.asText())) {
                    ids.add(member);
                }
            }
        }
        return ids;
    }

    private static void collectMine(List<ObjectNode> ordered, Set<String> seen, JsonNode posts, ObjectNode board, String viewer, String group) {
        if (posts == null || !posts.isArray()) {
            return;
        }
        for (JsonNode post : posts) {
            String id = post.path("postId").asText("");
            if (!seen.add(id) || !(post instanceof ObjectNode object)) {
                continue;
            }
            if (!member(board, group, id, viewer)) {
                continue;
            }
            ordered.add(object.deepCopy());
        }
    }

    private static ObjectNode withPost(ObjectMapper json, ObjectNode note, String postId, String title, boolean reply) {
        ObjectNode copy = note.deepCopy();
        if (!postId.isEmpty()) {
            copy.put("postId", postId);
        }
        copy.put("postTitle", title);
        copy.put("reply", reply);
        return copy;
    }

    private static String titleOf(JsonNode feed, ObjectNode board, String postId) {
        String title = titleIn(feed == null ? null : feed.path("posts"), postId);
        return title.isEmpty() ? titleIn(board.path("posts"), postId) : title;
    }

    private static String titleIn(JsonNode posts, String postId) {
        if (posts == null) {
            return "";
        }
        for (JsonNode post : posts) {
            if (postId.equals(post.path("postId").asText())) {
                return post.path("title").asText("");
            }
        }
        return "";
    }

    private static boolean hasTopic(JsonNode post, long topicId) {
        for (JsonNode topic : post.path("topics")) {
            if (topic.path("id").canConvertToLong() && topic.path("id").asLong() == topicId) {
                return true;
            }
        }
        return false;
    }

    private static ObjectNode slice(ObjectMapper json, String field, List<ObjectNode> ordered, int page, int pageSize) {
        ObjectNode body = json.createObjectNode();
        int total = ordered.size();
        int from = Math.max(0, (page - 1) * pageSize);
        ArrayNode rows = body.putArray(field);
        if (from < total) {
            int to = Math.min(from + pageSize, total);
            for (int i = from; i < to; i++) {
                rows.add(ordered.get(i));
            }
        }
        body.put("total", total);
        body.put("page", page);
        body.put("pages", total == 0 ? 0 : (total + pageSize - 1) / pageSize);
        return body;
    }
}
