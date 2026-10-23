package com.zhiquan.sport.store.db;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseKeysTest {

    @Test
    void sortsParamsAndDropsToken() {
        String left = DatabaseKeys.objectKey("database/", "/team/getTeamRankByTeamId",
                Map.of("year", "2026-2027", "type", "1", "token", "abc", "competitionId", "82"));
        String right = DatabaseKeys.objectKey("database/", "prod-api/team/getTeamRankByTeamId",
                Map.of("competitionId", "82", "langtype", "zh", "type", "1", "year", "2026-2027"));
        assertEquals(left, right);
        assertEquals("database/team/getTeamRankByTeamId/competitionId=82/langtype=zh/type=1/year=2026-2027.json", left);
    }
}
