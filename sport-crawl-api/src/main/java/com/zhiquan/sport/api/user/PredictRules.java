package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预测按下注当时的盘口结算。赔率大于等于 1 按欧洲赔率，小于 1 按水位（先加 1）。
 * 让球、大小的四分之一盘把本金分成两半。
 */
public final class PredictRules {

    private PredictRules() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bet(String id, String userId, String phone, String nick, String mid, int type, String market,
                      int betvalue, String label, String line, String odds, int stake, String status,
                      int returned, int profit, String placedAt, String settledAt, String clientId) {
    }

    public record Outcome(String status, int returned, int profit) {
    }

    public record Quote(String label, String line, String odds, BigDecimal decimal) {
    }

    public record MatchEnd(String kind, int home, int away) {
    }

    public record RankRow(int rank, String userId, String nick, long profit, int win, int total) {
    }

    public record BoardView(List<RankRow> records, RankRow mine) {
    }

    public static String market(String raw) {
        String market = raw == null ? "" : raw.trim();
        if (!"rq".equals(market) && !"dx".equals(market) && !"sf".equals(market)) {
            throw new IllegalArgumentException("玩法不正确");
        }
        return market;
    }

    public static int sport(Integer raw) {
        int type = raw == null ? 1 : raw;
        if (type != 1 && type != 2 && type != 3) {
            throw new IllegalArgumentException("类型不正确");
        }
        return type;
    }

    public static int stake(Integer raw) {
        if (raw == null || raw < 1 || raw > 100_000) {
            throw new IllegalArgumentException("投注金额不正确");
        }
        return raw;
    }

    public static String clientId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String id = raw.trim();
        if (!id.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("投注编号不正确");
        }
        return id;
    }

    public static Quote readQuote(JsonNode root, String market, int betvalue) {
        JsonNode node = root == null ? null : root.path("data").path("forecast").path(market);
        JsonNode pankou = node == null ? null : node.path("pankou");
        if (pankou == null || !pankou.isArray() || pankou.size() < 3) {
            throw new IllegalArgumentException("这场还不能投注");
        }
        if ("sf".equals(market)) {
            if (betvalue < 1 || betvalue > 3) {
                throw new IllegalArgumentException("选项不正确");
            }
            BigDecimal decimal = decimal(pankou.get(betvalue - 1));
            return new Quote(label(market, betvalue), "", text(decimal), decimal);
        }
        if (betvalue != 1 && betvalue != 2) {
            throw new IllegalArgumentException("选项不正确");
        }
        BigDecimal line = line(pankou.get(1));
        BigDecimal decimal = decimal(pankou.get(betvalue == 1 ? 0 : 2));
        return new Quote(label(market, betvalue), line.stripTrailingZeros().toPlainString(), text(decimal), decimal);
    }

    public static MatchEnd readMatch(JsonNode root) {
        JsonNode info = root == null ? null : root.path("data").path("matchinfo");
        if (info == null || !info.isObject()) {
            return new MatchEnd("open", 0, 0);
        }
        String kind = finish(info.path("status_up_name").asText(""), info.path("status_up").asInt(-1), info.path("type").asInt(1));
        if (!"done".equals(kind)) {
            return new MatchEnd(kind, 0, 0);
        }
        int[] score = score(info.path("score").asText(""));
        if (score == null) {
            return new MatchEnd("open", 0, 0);
        }
        return new MatchEnd("done", score[0], score[1]);
    }

    public static int[] score(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.trim().split("\\s*-\\s*");
        if (parts.length != 2) {
            return null;
        }
        try {
            int home = Integer.parseInt(parts[0]);
            int away = Integer.parseInt(parts[1]);
            if (home < 0 || away < 0 || home > 99 || away > 99) {
                return null;
            }
            return new int[] {home, away};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String finish(String name, int code, int type) {
        String text = name == null ? "" : name;
        if (text.contains("完场")) {
            return "done";
        }
        if (text.contains("推迟") || text.contains("取消") || text.contains("腰斩") || text.contains("待定") || text.contains("延期")) {
            return "void";
        }
        if (type == 2 && code == 10) {
            return "done";
        }
        if (type != 2 && code == 8) {
            return "done";
        }
        return "open";
    }

    public static Outcome settle(Bet bet, int home, int away) {
        BigDecimal decimal = new BigDecimal(bet.odds());
        if ("sf".equals(bet.market())) {
            int winner = home > away ? 1 : home == away ? 2 : 3;
            if (bet.betvalue() != winner) {
                return new Outcome("lose", 0, -bet.stake());
            }
            int returned = payout(bet.stake(), decimal);
            return new Outcome(returned > bet.stake() ? "win" : "push", returned, returned - bet.stake());
        }
        BigDecimal line = bet.line() == null || bet.line().isBlank() ? BigDecimal.ZERO : new BigDecimal(bet.line());
        int[] parts = split(line);
        int returned = 0;
        int wins = 0;
        int pushes = 0;
        int loses = 0;
        for (int i = 0; i < parts.length; i++) {
            int half = parts.length == 1 ? bet.stake() : (i == 0 ? bet.stake() / 2 : bet.stake() - bet.stake() / 2);
            int diff = "dx".equals(bet.market())
                    ? (home + away) * 100 - parts[i]
                    : home * 100 + parts[i] - away * 100;
            int side = bet.betvalue() == 1 ? diff : -diff;
            if (side > 0) {
                returned += payout(half, decimal);
                wins++;
            } else if (side == 0) {
                returned += half;
                pushes++;
            } else {
                loses++;
            }
        }
        return status(bet.stake(), returned, wins, pushes, loses);
    }

    public static Outcome refund(Bet bet) {
        return new Outcome("refund", bet.stake(), 0);
    }

    public static BoardView rank(List<Bet> bets, String board, String period, LocalDate today, String userId) {
        String which = "roof".equals(board) ? "roof" : "profit".equals(board) ? "profit" : "";
        if (which.isEmpty()) {
            throw new IllegalArgumentException("榜单类型不正确");
        }
        Map<String, Agg> groups = new LinkedHashMap<>();
        if (bets != null) {
            for (Bet bet : bets) {
                if (bet == null || "pending".equals(bet.status()) || bet.userId() == null || bet.userId().isBlank()) {
                    continue;
                }
                LocalDate day = day(bet.settledAt());
                if (day == null || !inPeriod(day, period, today)) {
                    continue;
                }
                Agg agg = groups.computeIfAbsent(bet.userId(), key -> new Agg());
                agg.profit += bet.profit();
                if (!"refund".equals(bet.status())) {
                    agg.total++;
                    if ("win".equals(bet.status()) || "half-win".equals(bet.status())) {
                        agg.win++;
                    }
                }
                if (bet.nick() != null && !bet.nick().isBlank()) {
                    agg.nick = bet.nick();
                }
            }
        }
        List<Map.Entry<String, Agg>> rows = new ArrayList<>(groups.entrySet());
        rows.sort(Comparator.comparingLong((Map.Entry<String, Agg> entry) -> "roof".equals(which) ? entry.getValue().profit : -entry.getValue().profit)
                .thenComparing(Map.Entry::getKey));
        List<RankRow> top = new ArrayList<>();
        RankRow mine = new RankRow(0, userId == null ? "" : userId, "", 0, 0, 0);
        for (int i = 0; i < rows.size(); i++) {
            Map.Entry<String, Agg> entry = rows.get(i);
            RankRow row = new RankRow(i + 1, entry.getKey(), entry.getValue().nick, entry.getValue().profit, entry.getValue().win, entry.getValue().total);
            if (i < 50) {
                top.add(row);
            }
            if (entry.getKey().equals(userId)) {
                mine = row;
            }
        }
        return new BoardView(List.copyOf(top), mine);
    }

    public static boolean inPeriod(LocalDate day, String period, LocalDate today) {
        if ("day".equals(period)) {
            return day.equals(today);
        }
        if ("week".equals(period)) {
            LocalDate start = today.with(DayOfWeek.MONDAY);
            return !day.isBefore(start) && !day.isAfter(today);
        }
        if ("month".equals(period)) {
            return day.getYear() == today.getYear() && day.getMonth() == today.getMonth();
        }
        throw new IllegalArgumentException("榜单时间不正确");
    }

    private static Outcome status(int stake, int returned, int wins, int pushes, int loses) {
        int profit = returned - stake;
        if (returned <= 0) {
            return new Outcome("lose", 0, -stake);
        }
        if (wins == 0 && loses == 0) {
            return new Outcome("push", returned, profit);
        }
        if (wins > 0 && loses == 0 && pushes == 0) {
            return new Outcome(profit > 0 ? "win" : "push", returned, profit);
        }
        if (wins > 0 && loses == 0) {
            return new Outcome("half-win", returned, profit);
        }
        if (loses > 0 && wins == 0 && pushes > 0) {
            return new Outcome("half-lose", returned, profit);
        }
        return new Outcome(profit >= 0 ? "half-win" : "half-lose", returned, profit);
    }

    private static int payout(int stake, BigDecimal decimal) {
        return decimal.multiply(BigDecimal.valueOf(stake)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static int[] split(BigDecimal line) {
        int scaled = line.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        int mod = Math.floorMod(scaled, 100);
        if (mod == 25 || mod == 75) {
            return new int[] {scaled - 25, scaled + 25};
        }
        if (mod == 0 || mod == 50) {
            return new int[] {scaled};
        }
        throw new IllegalArgumentException("这场还不能投注");
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException("这个选项暂时不能投");
        }
        BigDecimal raw = node.decimalValue();
        if (raw.signum() <= 0 || raw.compareTo(new BigDecimal("1000")) > 0) {
            throw new IllegalArgumentException("这个选项暂时不能投");
        }
        if (raw.compareTo(BigDecimal.ONE) < 0) {
            return raw.add(BigDecimal.ONE);
        }
        return raw;
    }

    private static BigDecimal line(JsonNode node) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException("这场还不能投注");
        }
        return node.decimalValue();
    }

    private static String text(BigDecimal decimal) {
        return decimal.stripTrailingZeros().toPlainString();
    }

    private static String label(String market, int betvalue) {
        if ("rq".equals(market)) {
            return betvalue == 1 ? "主" : "客";
        }
        if ("dx".equals(market)) {
            return betvalue == 1 ? "大" : "小";
        }
        return betvalue == 1 ? "胜" : betvalue == 2 ? "平" : "负";
    }

    private static LocalDate day(String stamp) {
        if (stamp == null || stamp.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(stamp.substring(0, 10));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static final class Agg {
        private long profit;
        private int win;
        private int total;
        private String nick = "";
    }
}
