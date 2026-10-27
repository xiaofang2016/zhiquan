package com.zhiquan.sport.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityCardsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void keepsPublicPostFields() throws Exception {
        var post = json.readTree("""
                {"postId":5128,"title":"他们说让我大秀","content":"来个户外","createTime":"2026-10-27T07:49:16.000+00:00",
                 "nickname":"说球帝梦岚","head":"https://img/a.jpg","badgeName":"体验",
                 "likeCount":2,"commentCount":1,"viewCount":74,"auditStatus":"1","viewPermission":"0",
                 "lastCommentTime":"2026-10-27T08:43:39.000+00:00",
                 "circles":[{"circleId":25,"circleName":"百家荟"}],
                 "mediaUrls":[{"url":"https://img/p.jpg","type":"image"},{"url":"https://img/v.mp4","type":"video"}],
                 "redPacket":{"amount":"88.00","num":8,"remainNum":5,"statusName":"领取中"}}
                """);
        assertTrue(CommunityCards.visible(post));
        assertEquals("2026-10-27T08:43:39.000+00:00|1|2", CommunityCards.stamp(post));
        Map<String, Object> card = CommunityCards.post(post);
        assertEquals(5128L, card.get("postId"));
        assertEquals("百家荟", card.get("circleName"));
        assertEquals(java.util.List.of("https://img/p.jpg"), card.get("images"));
        @SuppressWarnings("unchecked")
        Map<String, Object> packet = (Map<String, Object>) card.get("redPacket");
        assertEquals(8, packet.get("num"));
    }

    @Test
    void dropsHiddenPostsAndEmptyPackets() throws Exception {
        var hidden = json.readTree("{\"postId\":1,\"viewPermission\":\"1\",\"auditStatus\":\"1\"}");
        assertFalse(CommunityCards.visible(hidden));
        var post = json.readTree("{\"postId\":2,\"auditStatus\":\"1\",\"viewPermission\":\"0\",\"redPacket\":null,\"circles\":[]}");
        assertNull(CommunityCards.post(post).get("redPacket"));
    }
}
