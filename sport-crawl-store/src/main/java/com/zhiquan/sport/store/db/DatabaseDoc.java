package com.zhiquan.sport.store.db;

public record DatabaseDoc(String key, String sha256, String tier, String fetchedAt, String dueAt, String path, String query) {
}
