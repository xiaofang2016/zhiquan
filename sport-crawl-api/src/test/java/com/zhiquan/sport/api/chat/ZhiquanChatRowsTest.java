package com.zhiquan.sport.api.chat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZhiquanChatRowsTest {

    @Test
    void paddedGroupIdBecomesTheNumericId() {
        var row = ZhiquanChatRows.group(Map.of(
                "sk", AttributeValue.fromS("group#0000000390"),
                "gid", AttributeValue.fromS("0000000390"),
                "kind", AttributeValue.fromS("group"),
                "name", AttributeValue.fromS("红包收米群"),
                "ownerName", AttributeValue.fromS("群主"),
                "updatedAt", AttributeValue.fromN("1730000000")));
        assertEquals(390L, row.id());
        assertEquals("0000000390", row.gid());
        assertEquals("红包收米群", row.name());
        assertEquals(1_730_000_000_000L, row.updatedAt());
        assertEquals("", row.notice());

        var announced = ZhiquanChatRows.group(Map.of(
                "gid", AttributeValue.fromS("0000008448"),
                "name", AttributeValue.fromS("戒赌吧"),
                "raw", AttributeValue.fromM(Map.of(
                        "g_notice", AttributeValue.fromS("不要通过微信转账"),
                        "g_notice_show_config", AttributeValue.fromS("{\"enabled\":1,\"allowClose\":1}")))));
        assertEquals("不要通过微信转账", announced.notice());

        var hidden = ZhiquanChatRows.group(Map.of(
                "gid", AttributeValue.fromS("0000008448"),
                "g_notice", AttributeValue.fromS("不要通过微信转账"),
                "g_notice_show_config", AttributeValue.fromS("{\"enabled\":0}")));
        assertEquals("", hidden.notice());

        var avatar = ZhiquanChatRows.group(Map.of(
                "gid", AttributeValue.fromS("0000008447"),
                "raw", AttributeValue.fromM(Map.of(
                        "group_avatar_file_name", AttributeValue.fromS("01c47016f1acca765c4ef6312ebcf104")))));
        assertEquals("01c47016f1acca765c4ef6312ebcf104", avatar.avatar());
        assertTrue(ZhiquanChatRows.olderCursor("0000008447", "group#0000008447#msg#0001793492389481#abcdef"));
        assertFalse(ZhiquanChatRows.olderCursor("0000008447", "group#0000000390#msg#0001793492389481#abcdef"));
        assertFalse(ZhiquanChatRows.olderCursor("0000008447", "group#0000008447#msg#"));
    }

    @Test
    void messageKeepsThePaddedClockAndTextType() {
        var row = ZhiquanChatRows.message(Map.of(
                "time", AttributeValue.fromN("1793492389481"),
                "nickName", AttributeValue.fromS("幸福"),
                "sendUid", AttributeValue.fromS("6584908"),
                "msgType", AttributeValue.fromS("90"),
                "content", AttributeValue.fromS("在")));
        assertEquals(1_793_492_389_481L, row.time());
        assertEquals("幸福", row.nick());
        assertEquals("6584908", row.sendUid());
        assertEquals("90", row.type());
        assertEquals("在", row.content());
        assertEquals("group#0000008447#msg#1", ZhiquanChatRows.message(Map.of(
                "time", AttributeValue.fromN("1"),
                "content", AttributeValue.fromS("在"),
                "sk", AttributeValue.fromS("group#0000008447#msg#1"))).id());

        var image = ZhiquanChatRows.message(Map.of(
                "time", AttributeValue.fromN("1793518706579"),
                "nickName", AttributeValue.fromS("吴彦祖"),
                "msgType", AttributeValue.fromN("13"),
                "content", AttributeValue.fromS("{\"files\":[{\"fileMd5\":\"1793518706579.jpg\"}]}")));
        assertEquals("13", image.type());

        var packet = ZhiquanChatRows.message(Map.of(
                "time", AttributeValue.fromN("1793521136005"),
                "msgType", AttributeValue.fromN("10"),
                "content", AttributeValue.fromS("{\"description\":\"每晚22:30开启红包雨\"}")));
        assertEquals("10", packet.type());
        assertTrue(packet.content().contains("每晚22:30开启红包雨"));

        var mention = ZhiquanChatRows.message(Map.of(
                "time", AttributeValue.fromN("1793521136005"),
                "msgType", AttributeValue.fromN("12"),
                "content", AttributeValue.fromS(
                        "{\"gid\":\"00000001042\",\"origin_text\":\"@包包揣金豆爱你\",\"select_obj\":[{\"nickname\":\"包包揣金豆\",\"user_uid\":\"2310686\"}]}")));
        assertEquals("@包包揣金豆爱你", mention.content());
        assertEquals("12", mention.type());
        assertTrue(ZhiquanChatRows.isClaimNotice("独自孤单已领取了红包"));
        assertTrue(ZhiquanChatRows.isClaimNotice("领取了红包"));
        assertFalse(ZhiquanChatRows.isClaimNotice("似有似无，等171/200人已领取了红包"));
        assertEquals("https://cdn.example.com", ZhiquanImageBase.fromResponse(
                "{\"success\":true,\"returnValue\":\"https://cdn.example.com/\"}"));
        assertEquals("", ZhiquanImageBase.fromResponse("{\"success\":false}"));
    }

    @Test
    void groupWithoutAnIdIsSkipped() {
        assertNull(ZhiquanChatRows.group(Map.of("name", AttributeValue.fromS("没有编号"))));
    }
}
