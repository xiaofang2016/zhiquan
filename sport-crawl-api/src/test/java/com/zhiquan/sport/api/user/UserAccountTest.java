package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
    void jxtSecretReadsBoximOutboundKey() {
        ObjectMapper json = new ObjectMapper();
        assertEquals("boxim-secret", JxtClient.plainSecret("{\"BOXIM_OUTBOUND_SECRET\":\"boxim-secret\"}", json));
        assertEquals("plain-secret", JxtClient.plainSecret(" plain-secret ", json));
    }

    @Test
    void jxtTransferUsesTheRedpacketSign() {
        assertEquals("1700000000000\nabc\nPOST\n/external/transfer/cross-platform\n{}",
                JxtBoximSign.payload("1700000000000", "abc", "POST", "/external/transfer/cross-platform", "{}"));
        assertEquals("ab1eb9e70f1d04248a586e3d56793c21d8512ec9786d1a21d425ee3879119a5d",
                JxtBoximSign.sign("test-secret", "1700000000000", "abc", "POST", "/external/transfer/cross-platform", "{}"));
    }

    @Test
    void jxtAmountUsesCentsAndTenYuanMinimum() {
        assertEquals(1000, JxtMoney.cents("10"));
        assertEquals(1050, JxtMoney.cents("10.5"));
        assertEquals(1000, JxtMoney.cents("10.00"));
        assertEquals("10.50", JxtMoney.yuan(1050));
        assertEquals("a***z", JxtBindService.mask("abcz"));
        assertEquals("10012", UedClient.MERCHANT_NO);
        assertThrows(IllegalArgumentException.class, () -> JxtMoney.cents("9.99"));
        assertThrows(IllegalArgumentException.class, () -> JxtMoney.cents("10.555"));
    }

    @Test
    void avatarAcceptsJpegAndStoresUnderAvatarPrefix() {
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
        String encoded = java.util.Base64.getEncoder().encodeToString(jpeg);
        assertEquals(4, AvatarImage.jpeg("data:image/jpeg;base64," + encoded).length);
        assertEquals("avatar/abc.jpg", AvatarImage.objectKey("abc"));
        assertEquals("/prod-api/user/avatar/abc?v=3", AvatarImage.path("abc", 3));
        assertThrows(IllegalArgumentException.class, () -> AvatarImage.jpeg("data:image/png;base64," + encoded));
        assertThrows(IllegalArgumentException.class, () -> AvatarImage.jpeg(""));
    }

    @Test
    void missionSignAndClaimFollowTheOriginalRules() throws Exception {
        LocalDateTime monday = LocalDateTime.of(2026, 10, 26, 8, 1, 2);
        MissionRules.State first = MissionRules.sign(MissionRules.fresh(), 1, monday);
        assertEquals(30, first.balance());
        assertEquals(1, first.signDay());
        assertEquals("2026-10-26", first.signedOn());
        assertEquals("每日签到", first.records().get(0).title());
        assertEquals("2026-10-26 08:01:02", first.records().get(0).time());
        assertThrows(IllegalArgumentException.class, () -> MissionRules.sign(first, 1, monday));
        assertThrows(IllegalArgumentException.class, () -> MissionRules.sign(first, 5, monday));
        MissionRules.State second = MissionRules.sign(first, 2, monday.plusDays(1));
        assertEquals(80, second.balance());
        assertEquals(2, second.signDay());
        MissionRules.State reset = MissionRules.sign(first, 1, monday.plusDays(2));
        assertEquals(1, reset.signDay());
        assertEquals(60, reset.balance());
        assertThrows(IllegalArgumentException.class, () -> MissionRules.claim(second, 3, monday.plusDays(1)));
        MissionRules.State third = MissionRules.sign(second, 3, monday.plusDays(2));
        MissionRules.State login = MissionRules.claim(third, 3, monday.plusDays(2));
        assertEquals(450, login.balance());
        assertTrue(login.gotLogin());
        assertThrows(IllegalArgumentException.class, () -> MissionRules.claim(login, 3, monday.plusDays(2)));
        MissionRules.State registered = MissionRules.claim(login, 1, monday.plusDays(2));
        assertEquals(650, registered.balance());
        assertThrows(IllegalArgumentException.class, () -> MissionRules.claim(registered, 1, monday.plusDays(2)));
        assertThrows(IllegalArgumentException.class, () -> MissionRules.claim(registered, 4, monday.plusDays(2)));
        ObjectMapper json = new ObjectMapper().registerModule(new ParameterNamesModule());
        MissionRules.State restored = json.readValue(json.writeValueAsString(registered), MissionRules.State.class);
        assertEquals(650, restored.balance());
        assertEquals("连续登录", restored.records().get(1).title());
    }

    @Test
    void redpackSplitsOneDailyPoolWithoutDoubleClaim() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 22, 5, 0);
        RedPackRules.Round round = RedPackRules.fresh();
        assertEquals(188, round.count());
        assertEquals(58800L, round.poolCent());
        Random random = new Random(1);
        long sum = 0;
        Set<String> users = new HashSet<>();
        for (int i = 0; i < RedPackRules.COUNT; i++) {
            String userId = "u" + i;
            RedPackRules.Round before = round;
            round = RedPackRules.claim(round, userId, now, random);
            long cent = round.claims().get(round.claims().size() - 1).cent();
            assertTrue(cent >= 1);
            sum += cent;
            users.add(userId);
            RedPackRules.Round taken = round;
            assertThrows(IllegalArgumentException.class, () -> RedPackRules.claim(taken, userId, now, random));
            assertEquals(before.remainCent() - cent, round.remainCent());
        }
        assertEquals(58800L, sum);
        assertEquals(0, round.remainCount());
        assertEquals(188, users.size());
        assertEquals(round.claims().get(0).cent(), RedPackRules.mine(round, "u0"));
        RedPackRules.Round finished = round;
        assertThrows(IllegalArgumentException.class, () -> RedPackRules.claim(finished, "extra", now, random));
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
