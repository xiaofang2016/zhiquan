package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.aws.Attrs;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.PhoneMove;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountWritesTest {

    private static SportUser named(String userId, String nickname) {
        SportUser user = new SportUser();
        user.setUserId(userId);
        user.setNickname(nickname);
        return user;
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void yinlangDebitShowsANegativeRow() {
        MissionRules.State next = MissionRules.change(new MissionRules.State(30, 1, "2026-11-09", false, false, List.of()),
                "送出足球射门", -10, NOW);
        assertEquals(20, next.balance());
        assertEquals(-10, next.records().get(0).amount());
        assertEquals("音浪不足", assertThrows(IllegalArgumentException.class,
                () -> MissionRules.change(next, "预测投注", -21, NOW)).getMessage());
        assertEquals(1, MissionRules.level(99));
        assertEquals(2, MissionRules.level(250));
    }

    @Test
    void onlyUpcomingMatchesCanBeBet() throws Exception {
        assertEquals("upcoming", PredictRules.phase(JSON.readTree("{\"data\":{\"matchinfo\":{\"status_up\":1,\"status_up_name\":\"未开赛\",\"name\":\"英超\",\"hteam_name\":\"阿森纳\",\"ateam_name\":\"切尔西\",\"matchtime\":\"2026-11-10 03:00:00\"}}}")));
        assertEquals("live", PredictRules.phase(JSON.readTree("{\"data\":{\"matchinfo\":{\"status_up\":4,\"status_up_name\":\"下半场\"}}}")));
        assertEquals("done", PredictRules.phase(JSON.readTree("{\"data\":{\"matchinfo\":{\"status_up\":8,\"status_up_name\":\"完场\"}}}")));
        assertEquals("这场已经开始", PredictRules.closed("live"));
        PredictRules.Fixture fixture = PredictRules.fixture(JSON.readTree("{\"data\":{\"matchinfo\":{\"name\":\"英超\",\"hteam_name\":\"阿森纳\",\"ateam_name\":\"切尔西\",\"matchtime\":\"2026-11-10 03:00:00\"}}}"));
        assertEquals("阿森纳", fixture.home());
        assertEquals("玩法不正确", assertThrows(IllegalArgumentException.class, () -> PredictRules.market("jq")).getMessage());
    }

    @Test
    void cashSignPaysTheSevenDayTableAndResets() {
        CashSignRules.State day1 = CashSignRules.sign(CashSignRules.fresh(), 1, NOW.toLocalDate());
        assertEquals(1, CashSignRules.yuan(day1.signDay()));
        assertEquals("今日已签到", assertThrows(IllegalArgumentException.class,
                () -> CashSignRules.sign(day1, null, NOW.toLocalDate())).getMessage());
        CashSignRules.State afterGap = CashSignRules.normalize(new CashSignRules.State(3, "2026-11-01"), NOW.toLocalDate());
        assertEquals(0, afterGap.signDay());
        CashSignRules.State finished = new CashSignRules.State(7, "2026-11-08");
        assertEquals(0, CashSignRules.normalize(finished, NOW.toLocalDate()).signDay());
        assertEquals(1, CashSignRules.shownYuan(finished, NOW.toLocalDate()));
        assertEquals("未到签到时间", assertThrows(IllegalArgumentException.class,
                () -> CashSignRules.sign(CashSignRules.fresh(), 2, NOW.toLocalDate())).getMessage());
    }

    @Test
    void giftPriceComesFromTheCatalog() throws Exception {
        var body = JSON.readTree("""
                {"code":"0","data":[{"id":10,"gift_name":"足球射门","gift_yinlang":1}]}
                """);
        GiftCatalog.Gift gift = GiftCatalog.find(body, 10);
        assertEquals(1, gift.yinlang());
        assertEquals(3, GiftCatalog.cost(gift.yinlang(), 3));
        assertEquals("送出了「足球射门」×3", GiftCatalog.line(gift.name(), 3));
        assertEquals("没有这个礼物", assertThrows(IllegalArgumentException.class,
                () -> { if (GiftCatalog.find(body, 9) == null) throw new IllegalArgumentException("没有这个礼物"); }).getMessage());
    }

    @Test
    void predictionSettlesHandicapAndScore() throws Exception {
        var tip = JSON.readTree("""
                {"data":{"forecast":{"rq":{"pankou":[0.6, -0.25, 1.6, 1]},"sf":{"pankou":[2.5, 3.2, 2.8, 1]},"dx":{"pankou":[0.9, 2.5, 0.9, 1]}}}}
                """);
        PredictRules.Quote home = PredictRules.readQuote(tip, "rq", 1);
        assertEquals("1.6", home.odds());
        assertEquals("-0.25", home.line());
        PredictRules.Outcome half = PredictRules.settle(sample("rq", 1, home.line(), home.odds(), 10), 1, 1);
        assertEquals("half-lose", half.status());
        assertEquals(5, half.returned());
        PredictRules.Quote over = PredictRules.readQuote(tip, "dx", 1);
        PredictRules.Outcome won = PredictRules.settle(sample("dx", 1, over.line(), over.odds(), 10), 1, 2);
        assertEquals("win", won.status());
        assertEquals(19, won.returned());
        PredictRules.Quote draw = PredictRules.readQuote(tip, "sf", 2);
        assertEquals("lose", PredictRules.settle(sample("sf", 2, "", draw.odds(), 10), 2, 1).status());
        assertEquals("done", PredictRules.finish("完场", 8, 1));
        assertEquals("void", PredictRules.finish("推迟", 9, 1));
        assertEquals("open", PredictRules.finish("下半场", 4, 1));
        var detail = JSON.readTree("{\"data\":{\"matchinfo\":{\"status_up_name\":\"完场\",\"status_up\":8,\"type\":1,\"score\":\"1-2\"}}}");
        assertEquals(2, PredictRules.readMatch(detail).away());
    }

    @Test
    void rankPutsLossesOnTheRoof() {
        LocalDate today = LocalDate.of(2026, 11, 9);
        List<PredictRules.Bet> bets = List.of(
                settled("a", "甲", 20, "2026-11-09 10:00:00"),
                settled("b", "乙", -50, "2026-11-09 11:00:00"));
        PredictRules.BoardView profit = PredictRules.rank(bets, "profit", "day", today, "b");
        assertEquals("a", profit.records().get(0).userId());
        assertEquals(2, profit.mine().rank());
        PredictRules.BoardView roof = PredictRules.rank(bets, "roof", "week", today, "b");
        assertEquals("b", roof.records().get(0).userId());
        assertEquals("小球", PredictService.rankNick(new PredictRules.RankRow(0, "me", "", 0, 0, 0, ""), named("me", "小球")));
        assertEquals("下注时的名字", PredictService.rankNick(new PredictRules.RankRow(1, "me", "下注时的名字", 10, 1, 1, ""), named("me", "小球")));
        assertEquals("", PredictService.rankNick(new PredictRules.RankRow(2, "other", "", 0, 0, 0, ""), named("me", "小球")));
        assertFalse(PredictRules.inPeriod(today.minusDays(8), "week", today));
        assertTrue(PredictRules.inPeriod(today.with(java.time.DayOfWeek.MONDAY), "week", today));
    }

    @Test
    void phoneMoveRewritesTheKeyAndSkipsCodes() {
        var item = new java.util.HashMap<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>();
        item.put("pk", Attrs.s("13800138000"));
        item.put("sk", Attrs.s("PROFILE"));
        item.put("phone", Attrs.s("13800138000"));
        item.put("user_id", Attrs.s("abc"));
        var moved = PhoneMove.copy(item, "13900139000");
        assertEquals("13900139000", moved.get("pk").s());
        assertEquals("13900139000", moved.get("phone").s());
        assertEquals("abc", moved.get("user_id").s());
        assertTrue(PhoneMove.skip("SMS"));
        assertFalse(PhoneMove.skip("PROFILE"));
    }

    private static PredictRules.Bet sample(String market, int betvalue, String line, String odds, int stake) {
        return new PredictRules.Bet("1", "u", "13800138000", "甲", "4498761", 1, market, betvalue, "", line, odds, stake,
                "pending", 0, 0, "", "", "", "", "", "", "", "");
    }

    private static PredictRules.Bet settled(String userId, String nick, int profit, String settledAt) {
        return new PredictRules.Bet(userId, userId, "13800138000", nick, "1", 1, "sf", 1, "胜", "", "2", 10,
                profit >= 0 ? "win" : "lose", 0, profit, "2026-11-09 09:00:00", settledAt, "", "", "", "", "", "");
    }
}
