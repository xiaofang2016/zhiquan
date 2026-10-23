package com.zhiquan.sport.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamRoomTest {

    @Test
    void closedRoomHasNoMessages() {
        UpstreamRoom room = UpstreamRoom.parse("{\"msg\":\"当前聊天室已关闭！\",\"code\":\"-1\"}");
        assertTrue(room.messages().isEmpty());
    }

    @Test
    void openRoomKeepsOnlyNewText() {
        String body = """
                {"code":"0","roomid":"88","msgs":[
                  {"id":"2","time":20,"fromNick":"甲","text":"后"},
                  {"msgid":"1","timetag":10,"nickname":"乙","content":"先"}
                ]}
                """;
        UpstreamRoom room = UpstreamRoom.parse(body);
        assertEquals("88", room.roomId());
        assertEquals(2, room.messages().size());
        assertEquals("先", room.messages().get(1).content());
    }
}
