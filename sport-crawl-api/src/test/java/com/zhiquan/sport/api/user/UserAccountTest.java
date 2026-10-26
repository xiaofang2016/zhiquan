package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountTest {

    @Test
    void phoneAndPasswordRules() {
        assertEquals("13800138000", AccountCheck.phone(" 13800138000 "));
        assertEquals("138****8000", AccountCheck.mask("13800138000"));
        assertThrows(IllegalArgumentException.class, () -> AccountCheck.phone("23800138000"));
        assertThrows(IllegalArgumentException.class, () -> AccountCheck.password("12345"));
        assertThrows(IllegalArgumentException.class, () -> AccountCheck.confirm("123456", "123457"));
        assertEquals("123456", AccountCheck.code(" 123456 "));
        assertThrows(IllegalArgumentException.class, () -> AccountCheck.code("12345"));
    }

    @Test
    void smsUsesMainlandTemplate() {
        Map<String, Object> body = NexsmsClient.payload("13800138000", "CN_OTP", "123456");
        assertEquals("+8613800138000", body.get("phone"));
        assertEquals("CN_OTP", body.get("templateId"));
        assertEquals("123456", ((Map<?, ?>) body.get("variables")).get("code"));
    }

    @Test
    void smsSecretAcceptsPlainOrJson() {
        ObjectMapper json = new ObjectMapper();
        assertEquals("sk_test", NexsmsSecret.apiKey(" sk_test ", json));
        assertEquals("sk_json", NexsmsSecret.apiKey("{\"api_key\":\"sk_json\"}", json));
        assertEquals("sk_only", NexsmsSecret.apiKey("{\"token\":\"sk_only\"}", json));
        assertThrows(IllegalArgumentException.class, () -> NexsmsSecret.apiKey("{\"a\":\"1\",\"b\":\"2\"}", json));
    }

    @Test
    void hashMatchesOnlyTheSamePassword() {
        String salt = UserPassword.salt();
        String hash = UserPassword.hash("secret1", salt);
        assertTrue(UserPassword.matches("secret1", salt, hash));
        assertFalse(UserPassword.matches("secret2", salt, hash));
    }

    @Test
    void jxtSignMatchesTheSharedVector() {
        assertEquals("sms\n13800138000", JxtSign.payload("sms", "13800138000"));
        assertEquals("aeba912f3fdd8dc73cc40d9a186e4c3c9514d2cb2afb1cc537516ff9b06c55da",
                JxtSign.sign("test-secret", "sms", "13800138000"));
    }

    @Test
    void jxtAmountUsesCentsAndTenYuanMinimum() {
        assertEquals(1000, JxtMoney.cents("10"));
        assertEquals(1050, JxtMoney.cents("10.5"));
        assertEquals(1000, JxtMoney.cents("10.00"));
        assertEquals("10.50", JxtMoney.yuan(1050));
        assertEquals("a***z", JxtBindService.mask("abcz"));
        assertThrows(IllegalArgumentException.class, () -> JxtMoney.cents("9.99"));
        assertThrows(IllegalArgumentException.class, () -> JxtMoney.cents("10.555"));
    }

    @Test
    void tokenRoundTripRejectsTampering() {
        String token = UserToken.issue("test-secret", "13800138000", "abc", 4_000_000_000L);
        UserToken.Session session = UserToken.read("test-secret", token, 1_700_000_000L);
        assertEquals("13800138000", session.phone());
        assertEquals("abc", session.userId());
        assertThrows(IllegalArgumentException.class, () -> UserToken.read("other-secret", token, 1_700_000_000L));
        assertThrows(IllegalArgumentException.class, () -> UserToken.read("test-secret", token, 4_000_000_001L));
    }
}
