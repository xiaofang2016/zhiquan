package com.zhiquan.sport.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.db.DatabaseTier;
import com.zhiquan.sport.store.entity.SportMatch;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabasePlanTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void readsCompetitionsYearsAndStages() throws Exception {
        var home = json.readTree("""
                {"hotList":[{"id":1,"sportsType":2},{"id":82,"sportsType":1}],
                 "fbList":[{"countryList":[{"competitionList":[{"id":82,"sportsType":1},{"id":120,"sportsType":1}]}]}],
                 "bbList":[{"id":3,"sportsType":2}]}
                """);
        List<DatabaseCatalog.Competition> competitions = DatabaseCatalog.competitions(home);
        assertEquals(4, competitions.size());
        assertTrue(competitions.contains(new DatabaseCatalog.Competition(82, 1)));
        assertTrue(competitions.contains(new DatabaseCatalog.Competition(1, 2)));

        var years = json.readTree("""
                [{"year":"2026-2027","is_current":1},{"year":"2025-2026","is_current":0}]
                """);
        assertEquals("2026-2027", DatabaseCatalog.currentYear(years));
        assertEquals(List.of("2025-2026"), DatabaseCatalog.otherYears(years, "2026-2027"));

        var stages = json.readTree("""
                [{"roundCount":38,"curRound":8,"isCurStage":1,"stageId":235026}]
                """);
        assertEquals(235026L, DatabaseCatalog.currentStages(stages).get(0).stageId());
        assertEquals(8, DatabaseCatalog.currentStages(stages).get(0).round());
    }

    @Test
    void nbaSeasonIncludesRankCallsAndPastStaysFrozen() {
        var nba = new DatabaseCatalog.Competition(1, 2);
        var season = DatabasePlan.season(nba, "2026-2027");
        assertTrue(season.stream().anyMatch(request -> request.path().equals("/team/getNbaTeamRank")
                && "1".equals(request.query().get("rankType"))));
        assertTrue(season.stream().anyMatch(request -> request.path().equals("/team/getNbaTeamRank")
                && "3".equals(request.query().get("rankType"))));
        assertEquals("/team/getBbYearListByCompetitionId", DatabasePlan.yearPath(nba));
        assertFalse(season.stream().anyMatch(request -> request.path().equals("/team/getFbCompetitionStats")));

        var premier = DatabasePlan.season(new DatabaseCatalog.Competition(82, 1), "2026-2027");
        assertTrue(premier.stream().anyMatch(request -> request.path().equals("/team/getFbCompetitionStats")));
        assertTrue(premier.stream().anyMatch(request -> "1".equals(request.query().get("isHome"))));
        assertTrue(premier.stream().anyMatch(request -> "0".equals(request.query().get("isHome"))));

        var past = DatabasePlan.past(new DatabaseCatalog.Competition(82, 1), "2025-2026");
        assertEquals(DatabaseTier.FROZEN, past.get(0).tier());
        assertFalse(past.stream().anyMatch(request -> request.path().contains("MatchList")));
    }

    @Test
    void liveWindowUsesKickoffWhenFinishTimeIsMissing() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 22, 0);
        assertTrue(LiveLeagues.include("live", null, now));
        assertTrue(LiveLeagues.include("done", now.minusHours(1), now));
        assertFalse(LiveLeagues.include("done", now.minusHours(6), now));
        assertFalse(LiveLeagues.include("wait", now.minusMinutes(10), now));

        SportMatch live = match(82, 1, now.minusHours(1), "下半场", 5);
        SportMatch old = match(120, 1, now.minusHours(8), "完场", 8);
        SportMatch show = match(9, 3, now.minusMinutes(5), "进行中", 4);
        List<DatabaseCatalog.Competition> selected = LiveLeagues.select(List.of(live, old, show), json, now);
        assertEquals(List.of(new DatabaseCatalog.Competition(82, 1)), selected);
        assertEquals("live", MatchPhase.of("下半场", 5));
        assertEquals("done", MatchPhase.of("完场", 8));
        assertEquals("wait", MatchPhase.of("未开赛", 1));
        assertEquals("live", MatchPhase.of(1, "2", 2));
        assertEquals("done", MatchPhase.of(1, "8", 8));
        assertEquals("live", MatchPhase.of(2, "8", 8));
        assertEquals("live", MatchPhase.of(2, "9", 9));
        assertEquals("done", MatchPhase.of(2, "10", 10));
        assertEquals("live", MatchPhase.of(2, "第一节完", 3));
        assertEquals("live", MatchPhase.of(3, "2", 2));
        assertEquals("done", MatchPhase.of(3, "3", 3));
    }

    private static SportMatch match(int cid, int type, LocalDateTime kickoff, String status, int code) {
        SportMatch row = new SportMatch();
        row.setCid(cid);
        row.setSportType(type);
        row.setMatchTime(kickoff);
        row.setPayload("{\"status_up_name\":\"" + status + "\",\"status_up\":" + code + "}");
        return row;
    }
}
