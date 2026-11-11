package com.zhiquan.sport.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchFiltersTest {

    @Test
    void leaguesSkipZeroAndCapAtTwenty() {
        assertEquals(java.util.List.of(), MatchFilters.leagues(null));
        assertEquals(java.util.List.of(), MatchFilters.leagues("0"));
        assertEquals(java.util.List.of(82, 120), MatchFilters.leagues("82, 120,82"));
        assertEquals(java.util.List.of(82), MatchFilters.leagues("0,82"));
        assertEquals("cid 格式不对", assertThrows(IllegalArgumentException.class, () -> MatchFilters.leagues("82,a")).getMessage());
        StringBuilder raw = new StringBuilder();
        for (int i = 1; i <= 21; i++) {
            if (i > 1) {
                raw.append(',');
            }
            raw.append(i);
        }
        assertEquals("cid 最多 20 个", assertThrows(IllegalArgumentException.class, () -> MatchFilters.leagues(raw.toString())).getMessage());
    }

    @Test
    void teamAndAnchor() {
        assertNull(MatchFilters.team("0"));
        assertNull(MatchFilters.team(" "));
        assertEquals(10149L, MatchFilters.team("10149"));
        assertEquals("teamId 格式不对", assertThrows(IllegalArgumentException.class, () -> MatchFilters.team("-1")).getMessage());
        assertTrue(MatchFilters.anchored(1));
        assertFalse(MatchFilters.anchored(0));
        assertFalse(MatchFilters.anchored(null));
    }
}
