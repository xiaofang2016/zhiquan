package com.zhiquan.sport.api.user;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 说球帝自己发的拼手气红包。每天北京时间一轮，188 个，共 588 元。
 * 金额记入说球帝余额，不经过原站。
 */
public final class RedPackRules {

    public static final int COUNT = 188;
    public static final long POOL_CENT = 58_800L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RedPackRules() {
    }

    public record Grab(String userId, long cent, String time, String nick, String avatar) {
    }

    public record Round(int count, long poolCent, int remainCount, long remainCent, List<Grab> claims) {
    }

    public static Round fresh() {
        return new Round(COUNT, POOL_CENT, COUNT, POOL_CENT, List.of());
    }

    public static Round claim(Round round, String userId, LocalDateTime now, Random random) {
        return claim(round, userId, "", "", now, random);
    }

    public static Round claim(Round round, String userId, String nick, String avatar, LocalDateTime now, Random random) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("请先登录");
        }
        List<Grab> claims = round.claims() == null ? List.of() : round.claims();
        for (Grab grab : claims) {
            if (userId.equals(grab.userId())) {
                throw new IllegalArgumentException("今日已领取");
            }
        }
        if (round.remainCount() <= 0 || round.remainCent() <= 0) {
            throw new IllegalArgumentException("红包已领完");
        }
        long cent = draw(round.remainCent(), round.remainCount(), random);
        List<Grab> next = new ArrayList<>(claims);
        next.add(new Grab(userId, cent, now.format(STAMP), nick == null ? "" : nick, avatar == null ? "" : avatar));
        return new Round(round.count(), round.poolCent(), round.remainCount() - 1, round.remainCent() - cent, next);
    }

    public static Long mine(Round round, String userId) {
        if (round.claims() == null || userId == null || userId.isBlank()) {
            return null;
        }
        for (Grab grab : round.claims()) {
            if (userId.equals(grab.userId())) {
                return grab.cent();
            }
        }
        return null;
    }

    static long draw(long remainCent, int remainCount, Random random) {
        if (remainCount <= 1) {
            return remainCent;
        }
        long leftForOthers = remainCount - 1L;
        long max = remainCent - leftForOthers;
        long cap = Math.max(1L, (remainCent / remainCount) * 2);
        long upper = Math.min(max, cap);
        if (upper < 1) {
            upper = 1;
        }
        return 1 + random.nextInt((int) upper);
    }
}
