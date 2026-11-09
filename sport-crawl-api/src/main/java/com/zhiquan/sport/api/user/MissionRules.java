package com.zhiquan.sport.api.user;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务中心的签到和领取。日期按北京时间，和原站一致。
 * 观看时长、分享次数、邀请人数没有来源，对应领取直接拒绝。
 */
public final class MissionRules {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int[] SIGN_REWARDS = {30, 50, 70, 90, 110, 130, 150};
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MissionRules() {
    }

    public record Entry(String title, String time, int amount) {
    }

    public record State(int balance, int signDay, String signedOn, boolean gotReg, boolean gotLogin, List<Entry> records) {
    }

    /** 不足 100 为 1 级，否则是余额除以 100。 */
    public static int level(int balance) {
        int level = balance / 100;
        return level < 1 ? 1 : level;
    }

    public static State fresh() {
        return new State(0, 0, "", false, false, List.of());
    }

    public static State normalize(State state, LocalDate today) {
        String signedOn = state.signedOn() == null ? "" : state.signedOn();
        List<Entry> records = state.records() == null ? List.of() : state.records();
        if (signedOn.isBlank() || signedOn.equals(today.toString()) || signedOn.equals(today.minusDays(1).toString())) {
            return new State(state.balance(), state.signDay(), signedOn, state.gotReg(), state.gotLogin(), records);
        }
        return new State(state.balance(), 0, "", state.gotReg(), state.gotLogin(), records);
    }

    public static State sign(State raw, int day, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        State state = normalize(raw, today);
        boolean signed = today.toString().equals(state.signedOn());
        if (day > state.signDay() + 1 && !signed) {
            throw new IllegalArgumentException("未到签到时间");
        }
        if (signed) {
            throw new IllegalArgumentException("今日已签到");
        }
        if (day < 1) {
            throw new IllegalArgumentException("未到签到时间");
        }
        int amount = SIGN_REWARDS[Math.min(state.signDay(), SIGN_REWARDS.length - 1)];
        return new State(state.balance() + amount, state.signDay() + 1, today.toString(),
                state.gotReg(), state.gotLogin(), prepend(state.records(), "每日签到", now, amount));
    }

    public static State claim(State raw, int type, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        State state = normalize(raw, today);
        if (type == 4) {
            throw new IllegalArgumentException("观看时长不足");
        }
        if (type == 1) {
            if (state.gotReg()) {
                throw new IllegalArgumentException("已领取");
            }
            return new State(state.balance() + 200, state.signDay(), state.signedOn(),
                    true, state.gotLogin(), prepend(state.records(), "首次注册成功", now, 200));
        }
        if (type == 3) {
            if (state.signDay() < 3) {
                throw new IllegalArgumentException("连续登录未满3天,请先完成签到");
            }
            if (state.gotLogin()) {
                throw new IllegalArgumentException("已领取");
            }
            return new State(state.balance() + 300, state.signDay(), state.signedOn(),
                    state.gotReg(), true, prepend(state.records(), "连续登录", now, 300));
        }
        throw new IllegalArgumentException("领取失败");
    }

    /** 音浪加减。delta 为负时余额不够抛出「音浪不足」。明细里扣减记负数。 */
    public static State change(State raw, String title, int delta, LocalDateTime now) {
        if (title == null || title.isBlank() || title.length() > 40 || delta == 0 || delta < -1_000_000 || delta > 1_000_000) {
            throw new IllegalArgumentException("领取失败");
        }
        State state = normalize(raw, now.toLocalDate());
        long next = (long) state.balance() + delta;
        if (next < 0) {
            throw new IllegalArgumentException("音浪不足");
        }
        if (next > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("领取失败");
        }
        return new State((int) next, state.signDay(), state.signedOn(), state.gotReg(), state.gotLogin(),
                prepend(state.records(), title, now, delta));
    }

    private static List<Entry> prepend(List<Entry> records, String title, LocalDateTime now, int amount) {
        List<Entry> next = new ArrayList<>();
        next.add(new Entry(title, now.format(STAMP), amount));
        if (records != null) {
            next.addAll(records);
        }
        // 明细保留最近 500 条，接口再按页截取。
        if (next.size() > 500) {
            return List.copyOf(next.subList(0, 500));
        }
        return List.copyOf(next);
    }

    public static List<Entry> page(List<Entry> records, int page, int pageSize) {
        List<Entry> all = records == null ? List.of() : records;
        int size = Math.min(Math.max(pageSize, 1), 100);
        int pn = Math.max(page, 1);
        int from = (pn - 1) * size;
        if (from >= all.size()) {
            return List.of();
        }
        int to = Math.min(from + size, all.size());
        return List.copyOf(all.subList(from, to));
    }
}
