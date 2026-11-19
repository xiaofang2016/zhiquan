package com.zhiquan.sport.api.user;

/**
 * 吉祥、UED 第一次设置登录密码。初始密码只用来试登录，不写入说球帝，也不打日志。
 */
final class VendorPassword {

    /** 对方短信自动建号时写入的登录密码。 */
    static final String INITIAL = "aa123456";

    static final String ALREADY = "已经设置过密码";

    private VendorPassword() {
    }

    static void check(String password, String confirm, String account, String phone, boolean rejectSame) {
        String value = password == null ? "" : password;
        if (value.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (!value.equals(confirm == null ? "" : confirm)) {
            throw new IllegalArgumentException("两次输入密码不一致");
        }
        if (rejectSame && sameAccount(value, account, phone)) {
            throw new IllegalArgumentException("密码不能与账号相同");
        }
    }

    /**
     * @param firstSet 对方 {@code is_first_set_pass}。读不到时传负数
     * @param loginMessage 用初始密码登录失败时的原文。登录成功不要传
     * @param loginSucceeded 初始密码登录成功，或异地校验说明密码是对的
     * @return {@code "1"} 还是初始密码，{@code "0"} 已经改过，{@code null} 判断不了
     */
    static String needAfter(int firstSet, String loginMessage, boolean loginSucceeded) {
        if (firstSet != 1) {
            return firstSet < 0 ? null : "0";
        }
        if (loginSucceeded) {
            return "1";
        }
        String message = loginMessage == null ? "" : loginMessage;
        if (message.contains("密码错误") || message.contains("不存在")) {
            return "0";
        }
        return null;
    }

    static boolean alreadyChanged(String message) {
        return "旧密码输入错误".equals(message);
    }

    private static boolean sameAccount(String password, String account, String phone) {
        if (phone != null && !phone.isBlank() && password.equals(phone)) {
            return true;
        }
        return account != null && !account.isBlank() && password.equals(account);
    }
}
