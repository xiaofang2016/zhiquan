package com.zhiquan.sport.api.user;

import java.util.Locale;

public final class JxtMoney {

    private JxtMoney() {
    }

    public static long cents(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!text.matches("\\d+(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("提现金额不正确");
        }
        String[] parts = text.split("\\.");
        long yuan = Long.parseLong(parts[0]);
        int frac = 0;
        if (parts.length == 2) {
            frac = Integer.parseInt(parts[1].length() == 1 ? parts[1] + "0" : parts[1]);
        }
        if (yuan > 1_000_000L) {
            throw new IllegalArgumentException("提现金额不正确");
        }
        long cents = yuan * 100 + frac;
        if (cents < 1000) {
            throw new IllegalArgumentException("提现金额不能小于10元");
        }
        if (cents > 100_000_000L) {
            throw new IllegalArgumentException("提现金额不正确");
        }
        return cents;
    }

    public static String yuan(long cents) {
        return String.format(Locale.ROOT, "%d.%02d", cents / 100, Math.abs(cents % 100));
    }
}
