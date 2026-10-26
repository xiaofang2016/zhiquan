package com.zhiquan.sport.api.user;

public record UserRequest(String phone, String password, String confirm, String code, String token, String amount) {
}
