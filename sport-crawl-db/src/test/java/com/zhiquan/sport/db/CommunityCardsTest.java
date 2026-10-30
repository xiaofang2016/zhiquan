package com.zhiquan.sport.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.community.CommunityCards;
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
        assertEquals("2026-10-27T08:43:39.000+00:00|1|2|74|0|v3", CommunityCards.stamp(post));
        Map<String, Object> card = CommunityCards.post(post);
        assertEquals(5128L, card.get("postId"));
        assertEquals("百家荟", card.get("circleName"));
        assertEquals(java.util.List.of("https://img/p.jpg"), card.get("images"));
        @SuppressWarnings("unchecked")
        Map<String, Object> packet = (Map<String, Object>) card.get("redPacket");
        assertEquals(8, packet.get("num"));
        @SuppressWarnings("unchecked")
        Map<String, Object> video = ((java.util.List<Map<String, Object>>) card.get("videos")).get(0);
        assertEquals("https://img/v.mp4", video.get("url"));
        assertEquals("https://img.cos.ap-guangzhou.myqcloud.com/p.jpg?imageMogr2/thumbnail/800x/format/webp",
                CommunityCards.thumb("https://img.cos.apguangzhou.myqcloud.com/p.jpg"));
        assertEquals(2, CommunityCards.shown(java.util.List.of(
                java.util.Map.of("replies", java.util.List.of(java.util.Map.of("replyId", 1))))));
    }

    @Test
    void keepsPicturesVideosRepliesAndCircleProfile() throws Exception {
        var post = json.readTree("""
                {"postId":5572,"title":"曼联","content":"#男人的收藏#","createTime":"2026-10-28T01:00:00.000+00:00",
                 "lastCommentTime":"2026-10-28T02:00:00.000+00:00","nickname":"甲","head":"https://img/h.jpg",
                 "userId":12,"badgeName":"筑基","badgeLevel":4,"uedLevel":"4","cityName":"香港","isEssence":"1",
                 "likeCount":3,"commentCount":8,"viewCount":9,"favoriteCount":2,"auditStatus":"1","viewPermission":"0",
                 "topics":[{"topicId":188,"topicName":"男人的收藏"}],
                 "circles":[{"circleId":14,"circleName":"足球热聊"}],
                 "mediaUrls":[
                   {"url":"https://img/p.jpg","type":"image","width":792,"height":1330},
                   {"url":"/comment-api/video/hls/m3u8?taskId=7","type":"video","width":720,"height":1280,"cover":"https://img/c.jpg"}
                 ],
                 "matches":[{"matchId":99,"type":1,"homeTeamName":"主队","awayTeamName":"客队","homeTeamLogo":"https://img/h.png","awayTeamLogo":"https://img/a.png"}],
                 "topHighlightComment":{"commentId":3,"nickname":"乙","content":"亮一下","likeCount":7,"cityName":"廊坊市","auditStatus":"1"},
                 "redPacket":{"walletId":"671044","amount":"20.00","num":20,"remainNum":16,"status":"1","statusName":"领取中","description":"恭喜发财"}}
                """);
        Map<String, Object> card = CommunityCards.post(post, "https://kzb001.net");
        assertEquals(true, card.get("essence"));
        assertEquals("香港", card.get("cityName"));
        assertEquals("2026-10-28T02:00:00.000+00:00", card.get("lastReplyTime"));
        assertEquals(12L, card.get("userId"));
        assertEquals(4, card.get("badgeLevel"));
        assertEquals(4, card.get("vipLevel"));
        assertEquals(4, card.get("uedLevel"));
        assertNull(card.get("jxfLevel"));
        assertNull(card.get("badgeStyle"));
        assertEquals(0, card.get("highlightCommentCount"));
        assertEquals(false, card.get("rewardEnabled"));
        assertEquals(2, card.get("collectCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> picture = ((java.util.List<Map<String, Object>>) card.get("pictures")).get(0);
        assertEquals(792, picture.get("w"));
        assertEquals(1330, picture.get("h"));
        @SuppressWarnings("unchecked")
        Map<String, Object> video = ((java.util.List<Map<String, Object>>) card.get("videos")).get(0);
        assertEquals("https://kzb001.net/comment-api/video/hls/m3u8?taskId=7", video.get("url"));
        assertEquals("https://img/c.jpg", video.get("cover"));
        @SuppressWarnings("unchecked")
        Map<String, Object> match = (Map<String, Object>) card.get("match");
        assertEquals(99L, match.get("id"));
        assertEquals("主队", match.get("home"));
        @SuppressWarnings("unchecked")
        Map<String, Object> topic = ((java.util.List<Map<String, Object>>) card.get("topics")).get(0);
        assertEquals(188L, topic.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> hot = (Map<String, Object>) card.get("hotComment");
        assertEquals("亮一下", hot.get("content"));
        assertNull(hot.get("userId"));
        assertEquals("", hot.get("head"));

        var comment = json.readTree("""
                {"commentId":9,"userId":8,"nickname":"丙","head":"","badgeName":"体验","badgeLevel":0,"uedLevel":"-1",
                 "content":"顶","createTime":"2026-10-28T01:00:00.000+00:00","cityName":"","likeCount":1,"replyCount":1,"auditStatus":"1",
                 "replies":[{"replyId":4,"userId":5,"nickname":"丁","head":"","badgeName":"体验","badgeLevel":1,"uedLevel":"3",
                   "content":"回","createTime":"2026-10-28T01:01:00.000+00:00","cityName":"上海","likeCount":0,
                   "replyToNickname":"丙","auditStatus":"1"}]}
                """);
        Map<String, Object> mapped = CommunityCards.comment(comment);
        assertEquals(9L, mapped.get("commentId"));
        assertEquals(null, mapped.get("vipLevel"));
        assertEquals(java.util.List.of(), mapped.get("imageUrls"));
        @SuppressWarnings("unchecked")
        Map<String, Object> reply = ((java.util.List<Map<String, Object>>) mapped.get("replies")).get(0);
        assertEquals("丙", reply.get("replyToNickname"));
        assertEquals(3, reply.get("vipLevel"));
        assertEquals(3, reply.get("uedLevel"));
        assertNull(CommunityCards.comment(json.readTree("{\"commentId\":1,\"auditStatus\":\"2\",\"content\":\"不行\"}")));

        var circle = json.readTree("""
                {"circleId":14,"circleName":"足球热聊","icon":"https://img/i.jpg","cover":"https://img/c.jpg",
                 "followCount":84,"postCount":1363,"groupAdminNickname":"假想敌","groupAdminHead":"https://img/o.jpg",
                 "topics":[{"topicId":15,"topicName":"天下足球","cover":"https://img/t.jpg"}]}
                """);
        Map<String, Object> profile = CommunityCards.circle(circle);
        assertEquals("假想敌", profile.get("ownerName"));
        assertEquals(84, profile.get("followCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> circleTopic = ((java.util.List<Map<String, Object>>) profile.get("topics")).get(0);
        assertEquals("https://img/t.jpg", circleTopic.get("cover"));
    }

    @Test
    void dropsHiddenPostsAndEmptyPackets() throws Exception {
        var hidden = json.readTree("{\"postId\":1,\"viewPermission\":\"1\",\"auditStatus\":\"1\"}");
        assertFalse(CommunityCards.visible(hidden));
        var post = json.readTree("{\"postId\":2,\"auditStatus\":\"1\",\"viewPermission\":\"0\",\"redPacket\":null,\"circles\":[]}");
        assertNull(CommunityCards.post(post).get("redPacket"));
    }
}
