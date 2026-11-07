package com.zhiquan.sport.api.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundLoginTest {

    private static final String SECRET = "boxim-secret";
    private static final String USER = "0c9a1e4b7d2f4a6e8b1c3d5f709182a4";
    private static final String PHONE = "13800138000";
    private static final long NOW = 1_700_000_000L;

    @Test
    void signatureOpensTheBoundToken() {
        String signature = JxtBoximSign.sign(SECRET, String.valueOf(NOW), "nonce1234567890ab", "POST",
                BoundLogin.PATH, BoundLogin.signed(USER, PHONE, "jxt"));
        BoundLogin.check(SECRET, String.valueOf(NOW), "nonce1234567890ab", USER, PHONE, "jxt", signature, NOW);
        assertEquals("token-1", BoundLogin.token(true, "token-1", NOW + 60, NOW, "jxt"));
    }

    @Test
    void wrongSignatureAndExpiredBindingAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> BoundLogin.check(SECRET, String.valueOf(NOW),
                "nonce1234567890ab", USER, PHONE, "jxt", "a".repeat(64), NOW));
        assertEquals("请先绑定UED账号", assertThrows(IllegalArgumentException.class,
                () -> BoundLogin.token(false, "token-1", NOW + 60, NOW, "ued")).getMessage());
        assertEquals("请重新验证吉祥账号", assertThrows(IllegalArgumentException.class,
                () -> BoundLogin.token(true, "token-1", NOW, NOW, "jxt")).getMessage());
    }

    @Test
    void liveBrandUsesTheSameSignature() {
        String signature = JxtBoximSign.sign(SECRET, String.valueOf(NOW), "nonce1234567890ab", "POST",
                BoundLogin.PATH, BoundLogin.signed(USER, PHONE, "live"));
        BoundLogin.check(SECRET, String.valueOf(NOW), "nonce1234567890ab", USER, PHONE, "live", signature, NOW);
    }
}
