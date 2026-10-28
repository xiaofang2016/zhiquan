package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakRulesTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void localChatSurvivesBesideUpstreamMessages() throws Exception {
        ObjectNode document = (ObjectNode) json.readTree("""
                {"matchId":"4498761","roomId":"room-1","closed":false,"syncedAt":8,
                 "messages":[{"id":"nim-1","time":1000,"nick":"原站","content":"你好","images":[{"url":"https://img.example/a.png"}]}]}
                """);
        ObjectNode spoken = ChatSpeak.append(json, document, "4498761", "不存在的人", "本地发言", 1000);
        assertTrue(spoken.path("id").asText().startsWith("local:"));
        assertEquals(1001, spoken.path("time").asLong());
        assertEquals(2, document.path("messages").size());
        assertEquals("https://img.example/a.png", document.path("messages").get(0).path("images").get(0).path("url").asText());
        assertEquals("room-1", document.path("roomId").asText());
        assertEquals(8, document.path("syncedAt").asInt());
    }

    @Test
    void closedRoomRejectsSpeech() {
        ObjectNode document = json.createObjectNode();
        document.put("closed", true);
        document.putArray("messages");
        assertThrows(IllegalArgumentException.class,
                () -> ChatSpeak.append(json, document, "1", "甲", "你好", 1));
    }

    @Test
    void localPostStaysWhenUpstreamFeedIsReplaced() throws Exception {
        ObjectNode board = CommunityBoard.ensure(json, json.createObjectNode());
        ObjectNode post = CommunityBoard.addPost(json, board, "不存在的人", "", "", "标题", "正文");
        CommunityBoard.addComment(json, board, post.path("postId").asText(), "另一人", "", "球迷", "回复");
        ObjectNode first = CommunityBoard.feed(json, json.readTree("""
                {"circles":[{"id":7,"name":"足球"}],"posts":[{"postId":5128,"title":"原站","content":"a",
                "createTime":"2026-10-27T07:49:16.000+00:00","nickname":"上游","head":"","badgeName":"",
                "likeCount":1,"commentCount":2,"viewCount":3,"circleIds":[7],"circleName":"足球","images":[],"redPacket":null}]}
                """), board);
        ObjectNode replaced = CommunityBoard.feed(json, json.readTree("""
                {"circles":[],"posts":[{"postId":6000,"title":"新的原站","content":"b",
                "createTime":"2026-10-28T01:00:00.000+00:00","nickname":"上游","head":"","badgeName":"",
                "likeCount":0,"commentCount":0,"viewCount":0,"circleIds":[],"circleName":"","images":[],"redPacket":null}]}
                """), board);
        assertTrue(containsPost(first, post.path("postId").asLong()));
        assertTrue(containsPost(replaced, post.path("postId").asLong()));
        assertFalse(containsPost(replaced, 5128));
        ObjectNode comments = CommunityBoard.comments(json, json.readTree("{\"comments\":[]}"), board, post.path("postId").asText());
        assertEquals(1, comments.path("comments").size());
        assertEquals("另一人", comments.path("comments").get(0).path("nickname").asText());
        assertEquals("球迷", comments.path("comments").get(0).path("badgeName").asText());
    }

    @Test
    void unknownNickIsAccepted() {
        assertEquals("路人甲", Voice.nick("路人甲"));
        assertThrows(IllegalArgumentException.class, () -> Voice.nick("  "));
        assertThrows(IllegalArgumentException.class, () -> AdminKey.check("", "secret"));
        assertThrows(IllegalArgumentException.class, () -> AdminKey.check("secret", "nope"));
        AdminKey.check("secret", "secret");
    }

    private static boolean containsPost(ObjectNode feed, long postId) {
        for (var post : feed.path("posts")) {
            if (post.path("postId").asLong() == postId) {
                return true;
            }
        }
        return false;
    }
}
