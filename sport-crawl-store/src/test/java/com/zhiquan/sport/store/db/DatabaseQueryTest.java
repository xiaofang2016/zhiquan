package com.zhiquan.sport.store.db;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseQueryTest {

    @Test
    void playerMatchNeedsTypeAndPlayerId() {
        assertFalse(DatabaseQuery.demandable("/player/queryFPlayerMatchByPlayerId", Map.of("langtype", "zh")));
        assertFalse(DatabaseQuery.demandable("/player/queryFPlayerMatchByPlayerId", Map.of("type", "1")));
        assertFalse(DatabaseQuery.demandable("/player/queryFPlayerMatchByPlayerId",
                Map.of("type", "1", "playerId", "abc")));
        assertTrue(DatabaseQuery.demandable("/player/queryFPlayerMatchByPlayerId",
                Map.of("type", "1", "playerId", "128")));
    }

    @Test
    void otherPathsStayDemandable() {
        assertTrue(DatabaseQuery.demandable("/team/getTeamBase", Map.of()));
        assertFalse(DatabaseQuery.demandable("/player/queryFPlayerSeasonDataByPlayerId",
                Map.of("type", "1", "playerId", "128")));
        assertTrue(DatabaseQuery.demandable("/player/queryFPlayerSeasonDataByPlayerId",
                Map.of("type", "1", "playerId", "128", "seasonId", "9")));
    }

    @Test
    void lineupNeedsTheRoundName() {
        assertFalse(DatabaseQuery.demandable("/team/getFbCompetitionBestLineup",
                Map.of("type", "1", "cid", "82", "year", "2026-2027")));
        assertTrue(DatabaseQuery.demandable("/team/getFbCompetitionBestLineup",
                Map.of("type", "1", "cid", "82", "year", "2026-2027", "stats", "第9轮")));
        assertFalse(DatabaseQuery.demandable("/manage/queryFManageHonorByManageId",
                Map.of("type", "1", "manageId", "8084")));
        assertTrue(DatabaseQuery.demandable("/manage/getManageInfoByManageId",
                Map.of("type", "1", "manageId", "8084")));
    }

    @Test
    void teamStatsNeedsSeason() {
        assertFalse(DatabaseQuery.demandable("/team/getTeamAndPlayerStatsList", Map.of("cid", "82")));
        assertTrue(DatabaseQuery.demandable("/team/getTeamAndPlayerStatsList",
                Map.of("cid", "82", "year", "2025-2026", "type", "1", "statsObj", "1", "statsType", "1")));
        assertFalse(DatabaseQuery.demandable("/team/getTeamAndPlayerStatsList",
                Map.of("cid", "82", "year", "this", "type", "1", "statsObj", "1", "statsType", "1")));
    }
}
