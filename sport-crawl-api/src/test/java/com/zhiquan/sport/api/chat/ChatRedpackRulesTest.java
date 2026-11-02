package com.zhiquan.sport.api.chat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRedpackRulesTest {

    @Test
    void openPacketCanBeClaimedUntilTheOriginClosesIt() {
        ChatRedpackRules.Packet open = ChatRedpackRules.read(
                "{\"walletId\":\"677845\",\"status\":\"1\",\"remainNum\":\"200\",\"description\":\"西甲赞助商\"}");
        assertEquals("677845", open.walletId());
        assertTrue(open.open());
        assertFalse(ChatRedpackRules.read("{\"walletId\":\"1\",\"status\":\"3\",\"remainNum\":\"0\"}").open());
        assertFalse(ChatRedpackRules.read("{\"walletId\":\"1\",\"status\":\"1\",\"remainNum\":\"0\"}").open());
        assertTrue(ChatRedpackRules.read("{\"walletId\":\"1\",\"status\":\"1\"}").open());
    }

    @Test
    void amountStaysInsideTheConfiguredRange() {
        assertEquals(10, ChatRedpackRules.draw(10, 10, new Random(1)));
        Random random = new Random(2);
        for (int i = 0; i < 20; i++) {
            long cent = ChatRedpackRules.draw(10, 30, random);
            assertTrue(cent >= 10 && cent <= 30);
        }
        assertThrows(IllegalArgumentException.class, () -> ChatRedpackRules.checkRange(0, 10));
        assertThrows(IllegalArgumentException.class, () -> ChatRedpackRules.checkRange(20, 10));
    }
}
