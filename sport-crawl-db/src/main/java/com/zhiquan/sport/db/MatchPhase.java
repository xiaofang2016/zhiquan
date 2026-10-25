package com.zhiquan.sport.db;

import java.util.regex.Pattern;

public final class MatchPhase {

    private static final Pattern QUARTER = Pattern.compile("第[一二三四]节");
    private static final Pattern LIVE = Pattern.compile("上半场|下半场|中场|进行|比赛中|加时|点球");
    private static final Pattern DONE = Pattern.compile("完|结束|取消|腰斩|推迟|中断|延期");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private MatchPhase() {
    }

    public static String of(String statusName, int statusUp) {
        return of(1, statusName, statusUp);
    }

    public static String of(int sportType, String statusName, int statusUp) {
        String name = statusName == null ? "" : statusName;
        if (QUARTER.matcher(name).find()) {
            return "live";
        }
        if (LIVE.matcher(name).find()) {
            return "live";
        }
        if (DONE.matcher(name).find()) {
            return "done";
        }
        if (name.isEmpty() || NUMBER.matcher(name).matches()) {
            return byCode(sportType, statusUp);
        }
        return "wait";
    }

    private static String byCode(int sportType, int code) {
        if (sportType == 2) {
            if (code >= 2 && code <= 9) {
                return "live";
            }
            if (code >= 10) {
                return "done";
            }
            return "wait";
        }
        if (sportType == 3) {
            if (code == 2) {
                return "live";
            }
            if (code >= 3) {
                return "done";
            }
            return "wait";
        }
        if (code >= 2 && code <= 7) {
            return "live";
        }
        if (code >= 8) {
            return "done";
        }
        return "wait";
    }
}
