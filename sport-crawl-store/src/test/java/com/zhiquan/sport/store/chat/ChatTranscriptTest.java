package com.zhiquan.sport.store.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTranscriptTest {

    @Test
    void firstWriteSetsVersionToTheTail() {
        ChatTranscript.Merge merged = ChatTranscript.append(ChatDocument.empty("4661272"), "room-1", List.of(
                new ChatMessage("2", 20, "a", "后"),
                new ChatMessage("1", 10, "b", "先")
        ));
        assertTrue(merged.changed());
        assertEquals("room-1", merged.document().roomId());
        assertEquals(List.of("1", "2"), merged.document().messages().stream().map(ChatMessage::id).toList());
        assertEquals(new ChatVersion(20, "2"), merged.document().version());
    }

    @Test
    void initKeepsTheLatestHundredAndHistorySitsInFront() {
        List<ChatMessage> all = new java.util.ArrayList<>();
        for (int i = 1; i <= 150; i++) {
            all.add(new ChatMessage(String.valueOf(i), i, "n", "m" + i));
        }
        ChatDocument seeded = ChatTranscript.append(ChatDocument.empty("4661272"), "room-1",
                all.subList(50, 150)).document();
        assertEquals(100, ChatTranscript.latest(seeded, 100).size());
        assertEquals("51", ChatTranscript.latest(seeded, 100).get(0).id());
        assertEquals("150", ChatTranscript.latest(seeded, 100).get(99).id());

        ChatTranscript.Merge merged = ChatTranscript.append(seeded, "room-1", List.of(
                new ChatMessage("150", 150, "n", "m150"),
                new ChatMessage("10", 10, "n", "更早"),
                new ChatMessage("151", 151, "n", "新")
        ));
        assertEquals(102, merged.document().messages().size());
        assertEquals("10", merged.document().messages().get(0).id());
        assertEquals(new ChatVersion(151, "151"), merged.document().version());
        assertEquals("52", ChatTranscript.latest(merged.document(), 100).get(0).id());
        assertEquals("151", ChatTranscript.latest(merged.document(), 100).get(99).id());
    }

    @Test
    void sameTextWithoutIdIsStoredOnce() {
        ChatMessage raw = new ChatMessage(null, 10, "观众", "你好");
        ChatDocument once = ChatTranscript.append(ChatDocument.empty("1"), null, List.of(raw)).document();
        ChatTranscript.Merge again = ChatTranscript.append(once, null, List.of(raw));
        assertFalse(again.changed());
        assertEquals(1, again.document().messages().size());
    }

    @Test
    void sameNumberIdIsNotStoredTwice() {
        ChatDocument once = ChatTranscript.append(ChatDocument.empty("1"), null, List.of(
                new ChatMessage("10", 10, "a", "原文")
        )).document();
        ChatTranscript.Merge again = ChatTranscript.append(once, null, List.of(
                new ChatMessage("10", 99, "a", "改写")
        ));
        assertFalse(again.changed());
        assertEquals("原文", again.document().messages().get(0).content());
    }
}
