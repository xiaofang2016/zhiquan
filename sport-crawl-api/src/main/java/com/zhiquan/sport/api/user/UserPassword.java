package com.zhiquan.sport.api.user;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class UserPassword {

    private static final int ITERATIONS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private UserPassword() {
    }

    public static String salt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String hash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(salt), ITERATIONS, 256);
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(encoded);
        } catch (Exception ex) {
            throw new IllegalStateException("密码无法保存", ex);
        }
    }

    public static boolean matches(String password, String salt, String hash) {
        if (password == null || salt == null || hash == null || salt.isBlank() || hash.isBlank()) {
            return false;
        }
        byte[] actual = hash(password, salt).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] expected = hash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }
}
