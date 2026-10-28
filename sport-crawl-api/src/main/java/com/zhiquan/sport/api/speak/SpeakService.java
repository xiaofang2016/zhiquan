package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.chat.ChatTranscript;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.S3JsonStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
        return update(boardKey(), board -> CommunityBoard.addPost(json, CommunityBoard.ensure(json, board),
                Voice.nick(nick), Voice.head(head), Voice.badge(badge), Voice.title(title), Voice.post(content)));
    }

    public ObjectNode comment(String postId, String nick, String head, String badge, String content) {
        String id = Voice.postId(postId);
        return update(boardKey(), board -> CommunityBoard.addComment(json, CommunityBoard.ensure(json, board), id,
                Voice.nick(nick), Voice.head(head), Voice.badge(badge), Voice.comment(content)));
    }

    public ObjectNode communityFeed(JsonNode upstream) {
        return CommunityBoard.feed(json, upstream, loadBoard());
    }

    public ObjectNode communityComments(JsonNode upstream, String postId) {
        return CommunityBoard.comments(json, upstream, loadBoard(), postId);
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
