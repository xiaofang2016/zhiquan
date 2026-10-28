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
}
