package com.zhiquan.sport.api.speak;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class AdminKey {

    private AdminKey() {
    }

    public static void check(String configured, String given) {
        byte[] expected = configured == null ? new byte[0] : configured.getBytes(StandardCharsets.UTF_8);
        byte[] actual = given == null ? new byte[0] : given.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || expected.length != actual.length || !MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("未授权");
        }
    }
}
