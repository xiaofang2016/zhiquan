package com.zhiquan.sport.api.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupRedpackTest {

    @Test
    void signatureAcceptsADebit() {
        String user = "0c9a1e4b7d2f4a6e8b1c3d5f709182a4";
        String signed = GroupRedpack.signed(user, "13800138000", "debit", "100", "rp-send-m1", "发红包");
        String signature = JxtBoximSign.sign("boxim-secret", "1700000000", "nonce1234567890ab", "POST",
                GroupRedpack.PATH, signed);
        assertDoesNotThrow(() -> GroupRedpack.check("boxim-secret", "1700000000", "nonce1234567890ab",
                user, "13800138000", "debit", "100", "rp-send-m1", "发红包", signature, 1_700_000_000L));
        assertThrows(IllegalArgumentException.class, () -> GroupRedpack.check("boxim-secret", "1700000000",
                "nonce1234567890ab", user, "13800138000", "debit", "100", "rp-send-m1", "发红包", "ab", 1_700_000_000L));
    }
}
