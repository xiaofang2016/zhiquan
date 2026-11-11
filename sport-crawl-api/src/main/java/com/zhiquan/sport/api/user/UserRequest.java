package com.zhiquan.sport.api.user;

public record UserRequest(String phone, String password, String confirm, String code, String token, String amount, String image, Integer day, Integer type, String kind, Integer page, Integer pageSize, String cityName, Long giftId, Integer count, String mid, String market, Integer betvalue, Integer stake, String period, String board, String clientId, String username) {
}
