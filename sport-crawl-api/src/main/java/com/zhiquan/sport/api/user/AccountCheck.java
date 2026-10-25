package com.zhiquan.sport.api.user;

public final class AccountCheck {

    private AccountCheck() {
    }

    public static String phone(String raw) {
        String phone = raw == null ? "" : raw.trim();
        if (!phone.matches("1[3-9]\\d{9}")) {
            throw new IllegalArgumentException("请输入正确的手机号");
        }
        return phone;
    }

    public static String password(String raw) {
        String password = raw == null ? "" : raw;
        if (password.length() < 6 || password.length() > 20) {
            throw new IllegalArgumentException("密码需要 6 到 20 位");
        }
        return password;
    }

    public static String code(String raw) {
        String code = raw == null ? "" : raw.trim();
        if (!code.matches("\\d{6}")) {
            throw new IllegalArgumentException("请输入6位验证码");
        }
        return code;
    }

    public static void confirm(String password, String again) {
        if (!password.equals(again == null ? "" : again)) {
            throw new IllegalArgumentException("两次输入密码不一致");
        }
    }

    public static String mask(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
