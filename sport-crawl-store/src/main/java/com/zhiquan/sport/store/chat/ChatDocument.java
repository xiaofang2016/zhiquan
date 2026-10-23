package com.zhiquan.sport.store.chat;

import java.util.List;

/**
 * 一场比赛对应一份文档。matchId 就是对象身份，不另建聊天室文档。
 */
public record ChatDocument(String matchId, String roomId, ChatVersion version, List<ChatMessage> messages) {

    public static ChatDocument empty(String matchId) {
        return new ChatDocument(matchId, null, null, List.of());
    }
}
