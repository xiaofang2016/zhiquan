package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.chat.ChatTranscript;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.S3JsonStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SpeakService {

    private static final int ATTEMPTS = 5;

    private final S3JsonStore objects;
    private final ObjectMapper json;
    private final SportAwsProperties aws;
    private final String adminKey;

    public SpeakService(S3JsonStore objects,
                        ObjectMapper json,
                        SportAwsProperties aws,
                        @Value("${sport.admin.key:}") String adminKey) {
        this.objects = objects;
        this.json = json;
        this.aws = aws;
        this.adminKey = adminKey;
    }

    public void checkAdmin(String key) {
        AdminKey.check(adminKey, key);
    }

    public ObjectNode speak(String matchId, String nick, String content) {
        String id = ChatTranscript.matchId(matchId);
        String key = prefix(aws.getChatPrefix(), "chat/") + id + ".json";
        return update(key, document -> ChatSpeak.append(json, document, id, Voice.nick(nick), Voice.chat(content), System.currentTimeMillis()));
    }

    public ObjectNode post(String nick, String head, String badge, String title, String content) {
        return post(nick, head, badge, title, content, "");
    }

    public ObjectNode post(String nick, String head, String badge, String title, String content, String userId) {
        return update(boardKey(), board -> CommunityBoard.addPost(json, CommunityBoard.ensure(json, board),
                Voice.nick(nick), Voice.head(head), Voice.badge(badge), Voice.title(title), Voice.post(content), userId));
    }

    public ObjectNode comment(String postId, String nick, String head, String badge, String content) {
        return comment(postId, nick, head, badge, content, List.of());
    }

    public ObjectNode comment(String postId, String nick, String head, String badge, String content, List<String> images) {
        return comment(postId, nick, head, badge, content, images, "");
    }

    public ObjectNode comment(String postId, String nick, String head, String badge, String content, List<String> images, String userId) {
        return comment(postId, nick, head, badge, content, images, userId, null, "");
    }

    public ObjectNode comment(String postId, String nick, String head, String badge, String content, List<String> images, String userId, String city, String notifyUser) {
        String id = Voice.postId(postId);
        List<String> pictures = Voice.images(images);
        return update(boardKey(), board -> {
            ObjectNode ensured = CommunityBoard.ensure(json, board);
            ObjectNode comment = CommunityBoard.addComment(json, ensured, id,
                    Voice.nick(nick), Voice.head(head), Voice.badge(badge), Voice.comment(content), pictures, userId);
            CommunityLife.city(comment, city);
            CommunityLife.remember(json, ensured, userId, Voice.nick(nick), Voice.head(head));
            CommunityLife.note(json, ensured, notifyUser, userId, Voice.nick(nick), head, "comment", id, comment.path("commentId").asText(), "评论了你的帖子");
            return comment;
        });
    }

    public ObjectNode reply(String postId, String commentId, String replyToUserId, String replyToNickname,
                            String nick, String head, String badge, String content, List<String> images) {
        return reply(postId, commentId, replyToUserId, replyToNickname, nick, head, badge, content, images, "");
    }

    public ObjectNode reply(String postId, String commentId, String replyToUserId, String replyToNickname,
                            String nick, String head, String badge, String content, List<String> images, String userId) {
        return reply(postId, commentId, replyToUserId, replyToNickname, nick, head, badge, content, images, userId, null, "", null);
    }

    public ObjectNode reply(String postId, String commentId, String replyToUserId, String replyToNickname,
                            String nick, String head, String badge, String content, List<String> images, String userId, String city, String notifyUser) {
        return reply(postId, commentId, replyToUserId, replyToNickname, nick, head, badge, content, images, userId, city, notifyUser, null);
    }

    public ObjectNode reply(String postId, String commentId, String replyToUserId, String replyToNickname,
                            String nick, String head, String badge, String content, List<String> images, String userId, String city, String notifyUser, String replyToReplyId) {
        String id = Voice.postId(postId);
        String parent = Voice.postId(commentId);
        List<String> pictures = Voice.images(images);
        return update(boardKey(), board -> {
            ObjectNode ensured = CommunityBoard.ensure(json, board);
            ObjectNode reply = CommunityBoard.addReply(json, ensured, id, parent,
                    replyToUserId, replyToNickname, Voice.nick(nick), Voice.head(head), Voice.badge(badge), Voice.comment(content), pictures, userId);
            if (replyToReplyId != null && replyToReplyId.matches("[0-9]{1,18}")) {
                reply.put("replyToReplyId", Long.parseLong(replyToReplyId));
            }
            CommunityLife.city(reply, city);
            CommunityLife.remember(json, ensured, userId, Voice.nick(nick), Voice.head(head));
            CommunityLife.note(json, ensured, notifyUser, userId, Voice.nick(nick), head, "reply", id, parent, "回复了你");
            return reply;
        });
    }

    public ObjectNode communityFeed(JsonNode upstream) {
        return CommunityBoard.feed(json, upstream, loadBoard());
    }

    public ObjectNode communityPost(JsonNode feed, JsonNode file, String postId, String viewer) {
        return CommunityBoard.find(feed, file, loadBoard(), postId, viewer);
    }

    public ObjectNode communityComments(JsonNode upstream, String postId, int page, int pageSize, String viewer, String sort) {
        return CommunityBoard.comments(json, upstream, loadBoard(), postId, page, pageSize, viewer, sort);
    }

    public ObjectNode communityHighlights(JsonNode upstream, String postId, String viewer) {
        return CommunityBoard.highlights(json, upstream, loadBoard(), postId, viewer);
    }

    public ObjectNode communityReplies(JsonNode upstream, String postId, String commentId, JsonNode fetched, int page, int pageSize, String sort, String viewer) {
        return CommunityBoard.replies(json, upstream, loadBoard(), postId, commentId, fetched, page, pageSize, sort, viewer);
    }

    public ObjectNode board() {
        return loadBoard();
    }

    public ObjectNode like(String userId, String postId) {
        return like(userId, postId, "", "", "");
    }

    public ObjectNode like(String userId, String postId, String authorId, String nick, String head) {
        String id = Voice.postId(postId);
        return update(boardKey(), board -> {
            ObjectNode result = CommunityBoard.like(json, CommunityBoard.ensure(json, board), userId, id);
            if (result.path("liked").asBoolean(false)) {
                CommunityLife.note(json, board, authorId, userId, nick, head, "like", id, "", "赞了你的帖子");
            }
            return result;
        });
    }

    public ObjectNode collect(String userId, String postId) {
        String id = Voice.postId(postId);
        return update(boardKey(), board -> CommunityBoard.collect(json, CommunityBoard.ensure(json, board), userId, id));
    }

    public ObjectNode followUser(String userId, String target) {
        return followUser(userId, target, "", "");
    }

    public ObjectNode followUser(String userId, String target, String nick, String head) {
        String id = Voice.personId(target);
        return update(boardKey(), board -> {
            ObjectNode result = CommunityBoard.followUser(json, CommunityBoard.ensure(json, board), userId, id);
            if (result.path("followed").asBoolean(false)) {
                CommunityLife.note(json, board, id, userId, nick, head, "follow", "", "", "关注了你");
            }
            return result;
        });
    }

    public ObjectNode followCircle(String userId, String circleId) {
        String id = Voice.postId(circleId);
        return update(boardKey(), board -> CommunityBoard.followCircle(json, CommunityBoard.ensure(json, board), userId, id));
    }

    public ObjectNode block(String userId, String target) {
        String id = Voice.personId(target);
        return update(boardKey(), board -> CommunityBoard.block(json, CommunityBoard.ensure(json, board), userId, id));
    }

    public ObjectNode report(String userId, String targetType, String targetId, String reason) {
        return update(boardKey(), board -> CommunityBoard.report(json, CommunityBoard.ensure(json, board), userId, targetType, targetId, reason));
    }

    public long takePacket(String userId, String postId) {
        String id = Voice.postId(postId);
        long[] cent = new long[1];
        update(boardKey(), board -> {
            CommunityBoard.ensure(json, board);
            cent[0] = PostRedPack.take(board, id, userId, ThreadLocalRandom.current());
            return board;
        });
        return cent[0];
    }

    public static String nickOf(SportUser user) {
        String nick = user.getNickname();
        if (nick == null || nick.isBlank()) {
            return "用户";
        }
        return nick.length() > 20 ? nick.substring(0, 20) : nick;
    }

    public static String headOf(SportUser user) {
        return user.getAvatar() == null ? "" : user.getAvatar();
    }

    public static String absoluteHead(String head, String base) {
        if (head == null || head.isBlank() || base == null || base.isBlank() || !head.startsWith("/")) {
            return head == null ? "" : head;
        }
        return base + head;
    }

    public ObjectNode publish(String nick, String head, String userId, String phone, String title, String content,
                              List<Long> circleIds, List<String> pictures, List<String> videos, List<Long> topicIds,
                              List<String> mentionUserIds, Long matchId, String matchType, String cityName) {
        return update(boardKey(), board -> CommunityLife.publish(json, CommunityBoard.ensure(json, board),
                Voice.nick(nick), Voice.head(head), userId, phone, Voice.title(title), Voice.post(content),
                circleIds, pictures, videos, topicIds, mentionUserIds, matchId, matchType, cityName));
    }

    public ObjectNode editPost(String viewer, String postId, String title, String content, List<Long> circleIds,
                               List<String> pictures, List<String> videos, List<Long> topicIds, List<String> mentionUserIds,
                               Long matchId, String matchType) {
        String id = Voice.postId(postId);
        return update(boardKey(), board -> CommunityLife.edit(json, CommunityBoard.ensure(json, board), viewer, id, title, content, circleIds, pictures, videos, topicIds, mentionUserIds, matchId, matchType));
    }

    public ObjectNode removePost(String viewer, String postId) {
        String id = Voice.postId(postId);
        return update(boardKey(), board -> CommunityLife.removePost(CommunityBoard.ensure(json, board), viewer, id));
    }

    public ObjectNode visibility(String viewer, String postId, String visibility) {
        String id = Voice.postId(postId);
        return update(boardKey(), board -> CommunityLife.visibility(CommunityBoard.ensure(json, board), viewer, id, visibility));
    }

    public ObjectNode rewardSwitch(String viewer, String postId) {
        String id = Voice.postId(postId);
        return update(boardKey(), board -> CommunityLife.rewardSwitch(CommunityBoard.ensure(json, board), viewer, id));
    }

    public String authorPhone(String postId) {
        return CommunityLife.rewardPhone(board(), Voice.postId(postId));
    }

    public ObjectNode commitReward(String postId, String userId, String nick, String head, long cents, boolean showHead) {
        String id = Voice.postId(postId);
        return update(boardKey(), board -> CommunityLife.commitReward(json, CommunityBoard.ensure(json, board), id, userId, nick, head, cents, showHead));
    }

    public ObjectNode rewardList(String postId, int page, int pageSize) {
        return CommunityLife.rewards(json, board(), Voice.postId(postId), page, pageSize);
    }

    public ObjectNode likeComment(String viewer, String commentId, String nick, String head, String postId, int base) {
        String id = Voice.postId(commentId);
        return update(boardKey(), board -> {
            ObjectNode ensured = CommunityBoard.ensure(json, board);
            String author = CommunityLife.author(ensured, id, false);
            return CommunityLife.likeNote(json, ensured, viewer, nick, head, id, false, author, postId, id, base);
        });
    }

    public ObjectNode likeReply(String viewer, String replyId, String nick, String head, String postId, String commentId, int base) {
        String id = Voice.postId(replyId);
        return update(boardKey(), board -> {
            ObjectNode ensured = CommunityBoard.ensure(json, board);
            String author = CommunityLife.author(ensured, id, true);
            return CommunityLife.likeNote(json, ensured, viewer, nick, head, id, true, author, postId, commentId, base);
        });
    }

    public ObjectNode removeComment(String viewer, String postId, String commentId) {
        return update(boardKey(), board -> CommunityLife.removeComment(json, CommunityBoard.ensure(json, board), viewer, Voice.postId(postId), Voice.postId(commentId)));
    }

    public ObjectNode removeReply(String viewer, String replyId) {
        return update(boardKey(), board -> CommunityLife.removeReply(json, CommunityBoard.ensure(json, board), viewer, Voice.postId(replyId)));
    }

    public ObjectNode messages(String viewer, String type, int page, int pageSize) {
        return CommunityLife.messages(json, board(), viewer, type, page, pageSize);
    }

    public ObjectNode unread(String viewer) {
        return CommunityLife.unread(json, board(), viewer);
    }

    public ObjectNode readMessages(String viewer, List<String> ids) {
        return update(boardKey(), board -> CommunityLife.readMessages(json, CommunityBoard.ensure(json, board), viewer, ids));
    }

    public ObjectNode profile(JsonNode feed, String userId, String viewer) {
        return CommunityLife.profile(json, feed, board(), userId, viewer);
    }

    public ObjectNode people(JsonNode feed, String userId, String viewer, boolean fans, int page, int pageSize) {
        return CommunityLife.people(json, feed, board(), userId, viewer, fans, page, pageSize);
    }

    public ObjectNode saveProfile(String userId, String intro, String bg, String cityName) {
        return update(boardKey(), board -> CommunityLife.saveProfile(json, CommunityBoard.ensure(json, board), userId, intro, bg, cityName));
    }

    public ObjectNode myPosts(JsonNode feed, String viewer, String group, int page, int pageSize) {
        return CommunityLife.myPosts(json, feed, board(), viewer, group, page, pageSize);
    }

    public ObjectNode clearMine(String viewer, String group) {
        return update(boardKey(), board -> {
            CommunityBoard.ensure(json, board);
            CommunityLife.clearMine(board, viewer, group);
            ObjectNode result = json.createObjectNode();
            result.put("cleared", true);
            return result;
        });
    }

    public ObjectNode myComments(JsonNode feed, JsonNode index, String viewer, int page, int pageSize) {
        return CommunityLife.myComments(json, feed, index, board(), viewer, page, pageSize);
    }

    public ArrayNode myCircles(JsonNode feed, String viewer) {
        return CommunityLife.myCircles(json, feed, board(), viewer);
    }

    public ObjectNode myBlocks(JsonNode feed, String viewer, int page, int pageSize) {
        return CommunityLife.myBlocks(json, feed, board(), viewer, page, pageSize);
    }

    public ObjectNode topic(JsonNode feed, long topicId) {
        return CommunityLife.topic(json, feed, topicId);
    }

    public ObjectNode announce(JsonNode feed, long announceId) {
        return CommunityLife.announce(feed, announceId);
    }

    private ObjectNode update(String key, Editor editor) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            S3JsonStore.Loaded loaded = objects.load(key);
            ObjectNode document = read(loaded.body());
            ObjectNode created = editor.edit(document);
            try {
                objects.store(key, json.writeValueAsBytes(document), loaded.etag());
                return created;
            } catch (S3Exception ex) {
                if (ex.statusCode() != 412) {
                    throw ex;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("写入失败", ex);
            }
        }
        throw new IllegalStateException("正在被同时更新");
    }

    private ObjectNode loadBoard() {
        S3JsonStore.Loaded loaded = objects.load(boardKey());
        return CommunityBoard.ensure(json, read(loaded.body()));
    }

    private ObjectNode read(byte[] body) {
        if (body == null || body.length == 0) {
            return json.createObjectNode();
        }
        try {
            JsonNode node = json.readTree(body);
            if (node instanceof ObjectNode object) {
                return object;
            }
        } catch (Exception ex) {
            return json.createObjectNode();
        }
        return json.createObjectNode();
    }

    private String boardKey() {
        return prefix(aws.getCommunityPrefix(), "community/") + "local/board.json";
    }

    private static String prefix(String configured, String fallback) {
        String prefix = configured == null || configured.isBlank() ? fallback : configured;
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    @FunctionalInterface
    private interface Editor {
        ObjectNode edit(ObjectNode document);
    }
}
