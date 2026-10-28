package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 往一场比赛的聊天文档里追加一条本地发言。已有字段原样留下，编号用 local: 开头，原站同步不会把它认成自己的消息。
 */
public final class ChatSpeak {

    private ChatSpeak() {
    }

    public static ObjectNode append(ObjectMapper json, ObjectNode document, String matchId, String nick, String content, long now) {
        if (document.path("closed").asBoolean(false)) {
            throw new IllegalArgumentException("当前聊天室已关闭");
        }
        if (!document.hasNonNull("matchId")) {
            document.put("matchId", matchId);
        }
        ArrayNode messages = document.path("messages").isArray()
                ? (ArrayNode) document.get("messages")
                : document.putArray("messages");
        long time = now;
        for (var message : messages) {
            long existing = message.path("time").asLong(0);
            if (existing >= time) {
                time = existing + 1;
            }
        }
        String id = "local:" + UUID.randomUUID().toString().replace("-", "");
        ObjectNode message = json.createObjectNode();
        message.put("id", id);
        message.put("time", time);
        message.put("nick", nick);
        message.put("content", content);
        messages.add(message);
        sort(messages);
        ObjectNode version = document.path("version").isObject()
                ? (ObjectNode) document.get("version")
                : document.putObject("version");
        version.put("lastTime", time);
        version.put("lastId", id);
        return message;
    }

    private static void sort(ArrayNode messages) {
        List<ObjectNode> rows = new ArrayList<>();
        messages.forEach(node -> {
            if (node instanceof ObjectNode object) {
                rows.add(object);
            }
        });
        rows.sort(Comparator.comparingLong((ObjectNode node) -> node.path("time").asLong())
                .thenComparing(node -> node.path("id").asText()));
        messages.removeAll();
        rows.forEach(messages::add);
    }
}
