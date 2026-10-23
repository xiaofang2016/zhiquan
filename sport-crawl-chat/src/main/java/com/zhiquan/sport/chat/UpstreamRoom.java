package com.zhiquan.sport.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 原站一场比赛的聊天室响应。房间关着时没有消息。
 */
public record UpstreamRoom(String roomId, List<ChatMessage> messages) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static UpstreamRoom parse(String body) {
        if (body == null || body.isBlank()) {
            return new UpstreamRoom(null, List.of());
        }
        try {
            JsonNode root = JSON.readTree(body);
            String code = root.path("code").asText("");
            if (!"0".equals(code)) {
                return new UpstreamRoom(null, List.of());
            }
            String roomId = text(root, "roomid", "roomId");
            List<ChatMessage> messages = new ArrayList<>();
            collect(root, messages);
            return new UpstreamRoom(roomId, List.copyOf(messages));
        } catch (Exception ex) {
            return new UpstreamRoom(null, List.of());
        }
    }

    private static void collect(JsonNode node, List<ChatMessage> messages) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                ChatMessage message = message(item);
                if (message != null) {
                    messages.add(message);
                }
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (String field : List.of("msgs", "messages", "msgList", "list", "data")) {
            collect(node.get(field), messages);
        }
    }

    private static ChatMessage message(JsonNode item) {
        if (item == null || !item.isObject() || item.has("code")) {
            return null;
        }
        String content = text(item, "content", "text", "body");
        if (content == null || content.isBlank()) {
            return null;
        }
        long time = item.path("time").asLong(item.path("timetag").asLong(item.path("msgTimestamp").asLong(0)));
        if (time > 0 && time < 1_000_000_000_000L) {
            time = time * 1000;
        }
        String id = text(item, "id", "msgid", "msgId");
        String nick = text(item, "nick", "fromNick", "nickname");
        return new ChatMessage(id, time, nick == null ? "" : nick, content);
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }
}
