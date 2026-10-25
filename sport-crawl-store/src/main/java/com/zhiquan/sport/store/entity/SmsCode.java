package com.zhiquan.sport.store.entity;

public record SmsCode(String code, long sentAt, long expireAt) {
}
