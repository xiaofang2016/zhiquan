package com.zhiquan.sport.api.user;

import java.time.LocalDate;

/**
 * 钱包页的 7 天现金签到。和任务中心的音浪签到分开。断签或满 7 天后从第 1 天再来。
 */
public final class CashSignRules {

    public static final int[] YUAN = {1, 2, 3, 5, 6, 8, 10};

    private CashSignRules() {
    }

    public record State(int signDay, String signedOn) {
    }

    public static State fresh() {
        return new State(0, "");
    }

    public static State normalize(State raw, LocalDate today) {
        int day = raw == null ? 0 : Math.max(raw.signDay(), 0);
        String signedOn = raw == null || raw.signedOn() == null ? "" : raw.signedOn();
        if (signedOn.equals(today.toString())) {
            return new State(Math.min(day, 7), signedOn);
        }
        if (signedOn.equals(today.minusDays(1).toString()) && day < 7) {
            return new State(day, signedOn);
        }
        return fresh();
    }

    public static State sign(State raw, Integer requestedDay, LocalDate today) {
        State state = normalize(raw, today);
        if (today.toString().equals(state.signedOn())) {
            throw new IllegalArgumentException("今日已签到");
        }
        int next = state.signDay() + 1;
        if (requestedDay != null && requestedDay != next) {
            throw new IllegalArgumentException("未到签到时间");
        }
        return new State(next, today.toString());
    }

    public static int yuan(int signDay) {
        int index = Math.min(Math.max(signDay, 1), YUAN.length) - 1;
        return YUAN[index];
    }

    /** 今天已经签过时是今天领到的金额，否则是下一次能领的金额。 */
    public static int shownYuan(State raw, LocalDate today) {
        State state = normalize(raw, today);
        if (today.toString().equals(state.signedOn())) {
            return yuan(state.signDay());
        }
        return yuan(state.signDay() + 1);
    }

    public static boolean canSign(State raw, LocalDate today) {
        State state = normalize(raw, today);
        return !today.toString().equals(state.signedOn());
    }
}
