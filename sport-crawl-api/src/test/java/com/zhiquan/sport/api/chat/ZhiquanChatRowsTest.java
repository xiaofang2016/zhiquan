package com.zhiquan.sport.api.chat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    }

    @Test
    void groupWithoutAnIdIsSkipped() {
        assertNull(ZhiquanChatRows.group(Map.of("name", AttributeValue.fromS("没有编号"))));
    }
}
