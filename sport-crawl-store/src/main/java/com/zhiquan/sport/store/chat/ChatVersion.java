package com.zhiquan.sport.store.chat;

/**
 * 文档写到的位置。下次只追加比这个位置更新的消息。
 */
public record ChatVersion(long lastTime, String lastId) {
}
