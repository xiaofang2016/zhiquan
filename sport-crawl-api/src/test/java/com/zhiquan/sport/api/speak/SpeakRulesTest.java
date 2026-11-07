package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.api.user.UserAuthService;
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
        assertEquals(1, comments.path("total").asInt());
    }

    @Test
    void feedPageKeepsCirclesAndCanOpenAnOlderPost() throws Exception {
        ObjectNode full = CommunityBoard.feed(json, json.readTree("""
                {"circles":[{"id":14,"name":"足球热聊"},{"id":22,"name":"精华圈"}],
                 "posts":[
                   {"postId":3,"title":"新","content":"","createTime":"2026-10-29T03:00:00.000+00:00","circleIds":[14],"essence":false,"commentCount":1},
                   {"postId":2,"title":"精","content":"","createTime":"2026-10-29T02:00:00.000+00:00","circleIds":[22],"essence":true,"commentCount":0},
                   {"postId":1,"title":"旧","content":"","createTime":"2026-10-29T01:00:00.000+00:00","circleIds":[14],"essence":false,"commentCount":4}
                 ]}
                """), null);
        ObjectNode first = CommunityBoard.page(json, full, 1, 2, null, null, null);
        assertEquals(2, first.path("posts").size());
        assertEquals(3, first.path("posts").get(0).path("postId").asLong());
        assertEquals(3, first.path("total").asInt());
        assertEquals(2, first.path("circles").size());
        ObjectNode circle = CommunityBoard.page(json, full, 1, 60, 14L, null, null);
        assertEquals(2, circle.path("total").asInt());
        ObjectNode essence = CommunityBoard.page(json, full, 1, 60, null, "essence", null);
        assertEquals(2, essence.path("posts").get(0).path("postId").asLong());
        ObjectNode older = CommunityBoard.page(json, full, 1, 60, null, null, "2026-10-29T02:00:00.000+00:00");
        assertEquals(1, older.path("posts").get(0).path("postId").asLong());
        ObjectNode stored = (ObjectNode) json.readTree("{\"postId\":9,\"title\":\"落下的\",\"commentCount\":4}");
        ObjectNode opened = CommunityBoard.find(json.readTree("{\"posts\":[]}"), stored, null, "9");
        assertEquals("落下的", opened.path("title").asText());
        assertEquals(4, opened.path("commentCount").asInt());
        assertEquals(null, CommunityBoard.find(json.readTree("{\"posts\":[]}"), json.createObjectNode(), null, "9"));
    }

    @Test
    void followAndLikeStayOnTheLocalBoard() throws Exception {
        ObjectNode board = CommunityBoard.ensure(json, json.createObjectNode());
        assertTrue(CommunityBoard.like(json, board, "u1", "9").path("liked").asBoolean());
        assertFalse(CommunityBoard.like(json, board, "u1", "9").path("liked").asBoolean());
        assertTrue(CommunityBoard.like(json, board, "u1", "9").path("liked").asBoolean());
        assertTrue(CommunityBoard.followUser(json, board, "u1", "12").path("followed").asBoolean());
        assertTrue(CommunityBoard.followCircle(json, board, "u1", "14").path("followed").asBoolean());
        ObjectNode full = CommunityBoard.feed(json, json.readTree("""
                {"circles":[],"posts":[
                  {"postId":9,"title":"甲","content":"","createTime":"2026-10-29T03:00:00.000+00:00","userId":12,"circleIds":[14],"likeCount":1,"collectCount":0,"commentCount":0},
                  {"postId":8,"title":"乙","content":"","createTime":"2026-10-29T02:00:00.000+00:00","userId":3,"circleIds":[22],"likeCount":0,"collectCount":0,"commentCount":0}
                ]}
                """), board);
        ObjectNode followed = CommunityBoard.page(json, full, 1, 60, null, "follow", null, board, "u1");
        assertEquals(1, followed.path("total").asInt());
        assertEquals(9, followed.path("posts").get(0).path("postId").asLong());
        assertEquals(2, followed.path("posts").get(0).path("likeCount").asInt());
        assertTrue(followed.path("posts").get(0).path("liked").asBoolean());
        assertTrue(followed.path("posts").get(0).path("followed").asBoolean());
        CommunityBoard.block(json, board, "u1", "12");
        ObjectNode hidden = CommunityBoard.page(json, full, 1, 60, null, null, null, board, "u1");
        assertEquals(1, hidden.path("total").asInt());
        assertEquals(8, hidden.path("posts").get(0).path("postId").asLong());
        CommunityBoard.report(json, board, "u1", "post", "9", "不适");
        assertEquals(1, board.path("reports").size());
        assertThrows(IllegalArgumentException.class, () -> CommunityBoard.report(json, board, "u1", "post", "9", ""));
    }

    @Test
    void postRedPackIsOursAndPaysOnce() throws Exception {
        ObjectNode board = CommunityBoard.ensure(json, json.createObjectNode());
        ObjectNode post = (ObjectNode) json.readTree("""
                {"postId":9,"redPacket":{"walletId":"671044","amount":"38.00","num":5,"remainNum":4}}
                """);
        PostRedPack.apply(post, board, null);
        assertEquals("10.00", post.path("redPacket").path("amount").asText());
        assertEquals(10, post.path("redPacket").path("num").asInt());
        assertEquals(10, post.path("redPacket").path("remainNum").asInt());
        assertTrue(post.path("redPacket").path("mine").isNull());
        assertTrue(post.path("redPacket").path("walletId").isMissingNode());
        java.util.Random random = new java.util.Random(1);
        long first = PostRedPack.take(board, "9", "u1", random);
        assertEquals(first, PostRedPack.take(board, "9", "u1", random));
        assertEquals(1, board.path("packets").path("9").size());
        long sum = first;
        for (int i = 2; i <= PostRedPack.COUNT; i++) {
            sum += PostRedPack.take(board, "9", "u" + i, random);
        }
        assertEquals(PostRedPack.POOL_CENT, sum);
        assertThrows(IllegalArgumentException.class, () -> PostRedPack.take(board, "9", "extra", random));
        PostRedPack.apply(post, board, "u1");
        assertEquals(0, post.path("redPacket").path("remainNum").asInt());
        assertEquals("已领完", post.path("redPacket").path("statusName").asText());
        assertFalse(post.path("redPacket").path("mine").isNull());
    }

    @Test
    void commentsSortAndRepliesStayWithThePost() throws Exception {
        ObjectNode board = CommunityBoard.ensure(json, json.createObjectNode());
        ObjectNode upstream = (ObjectNode) json.readTree("""
                {"comments":[
                  {"commentId":1,"nickname":"甲","content":"早","createTime":"2026-10-29T01:00:00.000+00:00","likeCount":3,"replyCount":1,
                   "replies":[{"replyId":11,"nickname":"乙","content":"嵌进来的","createTime":"2026-10-29T01:01:00.000+00:00","likeCount":0}]},
                  {"commentId":2,"nickname":"丙","content":"晚","createTime":"2026-10-29T03:00:00.000+00:00","likeCount":0,"replyCount":0,"replies":[]}
                ]}
                """);
        ObjectNode newest = CommunityBoard.comments(json, upstream, board, "9", 1, 50, null, "newest");
        assertEquals(2, newest.path("comments").get(0).path("commentId").asLong());
        ObjectNode liked = CommunityBoard.comments(json, upstream, board, "9", 1, 50, null, "like");
        assertEquals(1, liked.path("comments").get(0).path("commentId").asLong());
        ObjectNode highlights = CommunityBoard.highlights(json, upstream, board, "9", null);
        assertEquals(1, highlights.path("total").asInt());
        assertEquals(1, highlights.path("comments").get(0).path("commentId").asLong());
        ObjectNode extra = (ObjectNode) json.readTree("""
                {"replyId":12,"nickname":"丁","content":"补上的","createTime":"2026-10-29T02:00:00.000+00:00","likeCount":0}
                """);
        ObjectNode replies = CommunityBoard.replies(json, upstream, board, "9", "1", json.createArrayNode().add(extra), 1, 50, "oldest", null);
        assertEquals(2, replies.path("total").asInt());
        assertEquals(11, replies.path("replies").get(0).path("replyId").asLong());
        assertEquals(12, replies.path("replies").get(1).path("replyId").asLong());
        ObjectNode spoken = CommunityBoard.addReply(json, board, "9", "1", null, "甲", "戊", "", "", "再回", java.util.List.of("https://img.example/a.jpg"));
        assertEquals("", spoken.path("replyToNickname").asText());
        assertEquals("https://img.example/a.jpg", spoken.path("imageUrls").get(0).asText());
        ObjectNode again = CommunityBoard.replies(json, upstream, board, "9", "1", json.createArrayNode(), 1, 50, "newest", null);
        assertEquals(spoken.path("replyId").asLong(), again.path("replies").get(0).path("replyId").asLong());
        ObjectNode full = CommunityBoard.feed(json, json.readTree("""
                {"circles":[{"id":14,"name":"足球"},{"id":22,"name":"闲聊"}],
                 "posts":[
                   {"postId":3,"title":"新发","content":"","createTime":"2026-10-29T03:00:00.000+00:00","lastReplyTime":"2026-10-29T03:10:00.000+00:00","circleIds":[14],"essence":true},
                   {"postId":2,"title":"刚回","content":"","createTime":"2026-10-29T01:00:00.000+00:00","lastReplyTime":"2026-10-29T04:00:00.000+00:00","circleIds":[22],"essence":false}
                 ]}
                """), null);
        ObjectNode byReply = CommunityBoard.page(json, full, 1, 60, null, null, null, null, null, "comment");
        assertEquals(2, byReply.path("posts").get(0).path("postId").asLong());
        assertEquals(1, CommunityBoard.essenceCircles(json, full).size());
        assertEquals(14, CommunityBoard.essenceCircles(json, full).get(0).path("id").asLong());
    }

    @Test
    void ownPostsKeepTheAccountAndReplyCountsMatchBodies() throws Exception {
        ObjectNode board = CommunityBoard.ensure(json, json.createObjectNode());
        String user = "0123456789abcdef0123456789abcdef";
        ObjectNode post = CommunityBoard.addPost(json, board, "我", "/prod-api/user/avatar/a.jpg", "", "标题", "正文", user);
        assertEquals(user, post.path("userId").asText());
        CommunityBoard.absoluteHeads(post, "https://dgtplaoir00la.cloudfront.net");
        assertEquals("https://dgtplaoir00la.cloudfront.net/prod-api/user/avatar/a.jpg", post.path("head").asText());
        ObjectNode upstream = (ObjectNode) json.readTree("""
                {"comments":[{"commentId":8,"userId":8,"nickname":"甲","content":"楼","createTime":"2026-10-29T01:00:00.000+00:00","likeCount":0,"replyCount":6,
                  "replies":[
                    {"replyId":1,"userId":8,"nickname":"甲","replyToUserId":8,"replyToNickname":"甲","content":"自己","createTime":"2026-10-29T01:01:00.000+00:00"},
                    {"replyId":2,"userId":5,"nickname":"乙","replyToUserId":9,"replyToNickname":"丙","content":"别人","createTime":"2026-10-29T01:02:00.000+00:00"},
                    {"replyId":3,"userId":9,"nickname":"丙","content":"再","createTime":"2026-10-29T01:03:00.000+00:00"},
                    {"replyId":4,"userId":5,"nickname":"乙","replyToUserId":8,"replyToNickname":"甲","replyToReplyId":1,"content":"回作者自己","createTime":"2026-10-29T01:04:00.000+00:00"}
                  ]}]}
                """);
        ObjectNode page = CommunityBoard.comments(json, upstream, board, "9", 1, 50, null, "newest");
        assertEquals(4, page.path("comments").get(0).path("replyCount").asInt());
        assertFalse(page.path("comments").get(0).path("replies").get(0).has("replyToNickname"));
        assertEquals(1, page.path("comments").get(0).path("replies").get(3).path("replyToReplyId").asLong());
        assertEquals(8, page.path("comments").get(0).path("replies").get(3).path("replyToUserId").asLong());
        ObjectNode rows = CommunityBoard.replies(json, upstream, board, "9", "8", json.createArrayNode(), 1, 50, "oldest", null);
        assertEquals(4, rows.path("total").asInt());
        assertFalse(rows.path("replies").get(0).has("replyToUserId"));
        assertEquals(9, rows.path("replies").get(1).path("replyToUserId").asLong());
        assertEquals("甲", rows.path("replies").get(3).path("replyToNickname").asText());
        ObjectNode circle = (ObjectNode) json.readTree("{\"id\":14,\"name\":\"足球\",\"followCount\":2}");
        CommunityBoard.followCircle(json, board, user, "14");
        CommunityBoard.markCircle(circle, board, user);
        assertTrue(circle.path("followed").asBoolean());
        assertEquals(3, circle.path("followCount").asInt());
        assertEquals(user, Voice.personId(user));
        ObjectNode hidden = CommunityLife.publish(json, board, "我", "", user, "13800000000", "仅自己", "正文", java.util.List.of(14L), null, null, null, null, null, null, null);
        CommunityLife.visibility(board, user, hidden.path("postId").asText(), "self");
        ObjectNode feed = CommunityBoard.feed(json, json.readTree("{\"circles\":[],\"posts\":[]}"), board);
        String hiddenId = hidden.path("postId").asText();
        assertFalse(listed(CommunityBoard.page(json, feed, 1, 20, null, null, null, board, "someone", "publish"), hiddenId));
        assertTrue(listed(CommunityBoard.page(json, feed, 1, 20, null, null, null, board, user, "publish"), hiddenId));
        ObjectNode comment = CommunityBoard.addComment(json, board, hidden.path("postId").asText(), "我", "", "", "说一句", java.util.List.of(), user);
        ObjectNode liked = CommunityLife.likeNote(json, board, user, "我", "", comment.path("commentId").asText(), false, user, hidden.path("postId").asText(), comment.path("commentId").asText(), 5);
        assertTrue(liked.path("liked").asBoolean());
        assertEquals(6, liked.path("likeCount").asInt());
        ObjectNode home = CommunityLife.profile(json, json.readTree("{\"posts\":[]}"), board, user, user);
        assertEquals("我", home.path("nickname").asText());
        CommunityBoard.addReply(json, board, hidden.path("postId").asText(), "8687", null, "", "我", "/prod-api/user/avatar/a.jpg", "", "回原站", java.util.List.of(), user);
        ObjectNode mine = CommunityLife.myComments(json, json.readTree("{\"posts\":[{\"postId\":" + hidden.path("postId").asText() + ",\"title\":\"仅自己\"}]}"), json.createObjectNode(), board, user, 1, 20);
        assertTrue(mine.path("comments").toString().contains(hidden.path("postId").asText()));
        assertTrue(mine.path("comments").toString().contains("仅自己"));
        CommunityLife.removeComment(json, board, user, hidden.path("postId").asText(), comment.path("commentId").asText());
        assertEquals("13800000000", CommunityLife.rewardPhone(board, hidden.path("postId").asText()));
        assertEquals("reply", CommunityBoard.report(json, board, user, "reply", "12", "内容不适").path("targetType").asText());
        String fan = "abcdef0123456789abcdef0123456789";
        ObjectNode fanPost = CommunityBoard.addPost(json, board, "粉", "", "", "粉丝帖", "正文", fan);
        CommunityBoard.followUser(json, board, fan, user);
        ObjectNode fans = CommunityBoard.page(json, CommunityBoard.feed(json, json.readTree("{\"circles\":[],\"posts\":[]}"), board), 1, 20, null, "fans", null, board, user, "publish");
        assertTrue(listed(fans, fanPost.path("postId").asText()));
        assertFalse(listed(fans, hiddenId));
        assertEquals(0, CommunityBoard.page(json, CommunityBoard.feed(json, json.readTree("{\"circles\":[],\"posts\":[]}"), board), 1, 20, null, "fans", null, board, null, "publish").path("total").asInt());
    }

    @Test
    void videoPlaylistPointsAtTheGateway() {
        assertEquals("https://dgtplaoir00la.cloudfront.net/prod-api/community/video/387/index.m3u8",
                CommunityVideo.playable("https://kzb001.net/comment-api/video/hls/m3u8?taskId=387", "https://dgtplaoir00la.cloudfront.net"));
        String rewritten = CommunityVideo.rewrite("""
                #EXT-X-KEY:METHOD=AES-128,URI="key?taskId=387"
                https://commentfile.yc12315.com/community/video/media/a/video0.ts?auth_key=1
                https://evil.example/secret.ts
                """, "387", "https://dgtplaoir00la.cloudfront.net");
        assertTrue(rewritten.contains("URI=\"https://dgtplaoir00la.cloudfront.net/prod-api/community/video/387/key\""));
        assertTrue(rewritten.contains("/prod-api/community/video/media?src="));
        assertTrue(rewritten.contains("https://evil.example/secret.ts"));
        assertEquals("commentfile.yc12315.com", CommunityVideo.segment("https://commentfile.yc12315.com/community/video/media/a/video0.ts").getHost());
        assertThrows(IllegalArgumentException.class, () -> CommunityVideo.segment("https://evil.example/community/video/a.ts"));
    }

    @Test
    void blankCityDoesNotReplaceTheAccountCity() {
        assertFalse(CommunityLife.rememberCity(null));
        assertFalse(CommunityLife.rememberCity("  "));
        assertTrue(CommunityLife.rememberCity("上海"));
        assertEquals("上海", CommunityLife.postedCity(null, "上海"));
        assertEquals("", CommunityLife.postedCity("", "上海"));
    }

    @Test
    void originalPostCannotBeRewardedAndMentionsNotify() throws Exception {
        ObjectNode board = CommunityBoard.ensure(json, json.createObjectNode());
        String author = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String friend = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        CommunityLife.remember(json, board, friend, "朋友", "/head.jpg");
        ObjectNode post = CommunityLife.publish(json, board, "我", "", author, "13800000000", "标题", "正文",
                null, null, null, null, java.util.List.of(friend), null, null, null);
        assertEquals("朋友", post.path("mentionUsers").get(0).path("nickname").asText());
        assertEquals("mention", board.path("messages").get(0).path("type").asText());
        assertEquals(friend, board.path("messages").get(0).path("toUserId").asText());
        ObjectNode upstream = (ObjectNode) json.readTree("""
                {"circles":[],"posts":[{"postId":9,"userId":"8","nickname":"原站","rewardEnabled":true,"createTime":"2026-10-29T01:00:00.000+00:00","title":"旧帖","content":"正文"}]}
                """);
        ObjectNode page = CommunityBoard.page(json, CommunityBoard.feed(json, upstream, board), 1, 20, null, null, null, board, null, "publish");
        boolean closed = false;
        for (JsonNode row : page.path("posts")) {
            if (row.path("postId").asLong() == 9) {
                closed = !row.path("rewardEnabled").asBoolean();
            }
        }
        assertTrue(closed);
        CommunityLife.rewardSwitch(board, author, post.path("postId").asText());
        ObjectNode mine = CommunityBoard.find(json.createObjectNode(), json.createObjectNode(), board, post.path("postId").asText(), null);
        assertTrue(mine.path("rewardEnabled").asBoolean());
        ObjectNode reward = CommunityLife.commitReward(json, board, post.path("postId").asText(), friend, "朋友", "/head.jpg", 100, false);
        assertEquals("", reward.path("head").asText());
        assertEquals("reward", board.path("messages").get(1).path("type").asText());
    }

    @Test
    void unknownNickIsAccepted() {
        assertEquals("路人甲", Voice.nick("路人甲"));
        assertEquals("上海", UserAuthService.city(" 上海 "));
        assertThrows(IllegalArgumentException.class, () -> UserAuthService.city("一二三四五六七八九十一二三四五六七八九十一"));
        assertThrows(IllegalArgumentException.class, () -> Voice.nick("  "));
        assertThrows(IllegalArgumentException.class, () -> AdminKey.check("", "secret"));
        assertThrows(IllegalArgumentException.class, () -> AdminKey.check("secret", "nope"));
        AdminKey.check("secret", "secret");
    }

    private static boolean listed(ObjectNode page, String postId) {
        for (JsonNode post : page.path("posts")) {
            if (postId.equals(post.path("postId").asText())) {
                return true;
            }
        }
        return false;
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
