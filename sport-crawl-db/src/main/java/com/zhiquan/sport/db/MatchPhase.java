package com.zhiquan.sport.db;

import java.util.regex.Pattern;

public final class MatchPhase {

    private static final Pattern LIVE = Pattern.compile("上半场|下半场|中场|进行|比赛中|加时|点球");
    private static final Pattern DONE = Pattern.compile("完|结束|取消|腰斩|推迟|中断");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private MatchPhase() {
    }

    public static String of(String statusName, int statusUp) {
        String name = statusName == null ? "" : statusName;
        if (LIVE.matcher(name).find()) {
            return "live";
        }
        if (DONE.matcher(name).find()) {
            return "done";
        }
        if (NUMBER.matcher(name).matches()) {
            if (statusUp >= 8) {
                return "done";
            }
            if (statusUp >= 3) {
                return "live";
            }
        }
        return "wait";
    }
}
