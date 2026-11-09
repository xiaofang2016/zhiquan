package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void matchSearchDropsPagesTheUpstreamDoesNotReturn() throws Exception {
        byte[] body = """
                {"code":"0","data":{"total":32,"totalPage":4,"currentPage":1,"dataList":[{},{}]}}
                """.getBytes();
        assertTrue(SearchPages.matchFirstPage("key=皇马&tab=1&pn=1&ps=10", body, JSON));
        byte[] clamped = SearchPages.clamp(body, JSON);
        assertEquals(2, JSON.readTree(clamped).path("data").path("total").asInt());
        assertEquals(1, JSON.readTree(clamped).path("data").path("totalPage").asInt());
        assertTrue(SearchPages.laterPageEmpty("{\"data\":{\"dataList\":[]}}".getBytes(), JSON));
        assertFalse(SearchPages.matchFirstPage("tab=2", body, JSON));
    }

    @Test
    void historyKeepsTheLatestWordAndDropsTheToken() {
        assertEquals("key=%E7%9A%87%E9%A9%AC&pid=4", SearchPages.withoutAccount("token=abc&key=%E7%9A%87%E9%A9%AC&uid=9&pid=4"));
        assertEquals("皇马", SearchPages.param("key=%E7%9A%87%E9%A9%AC", "key"));
        assertEquals(List.of("切尔西", "皇马"), SearchPages.remember(List.of("皇马", "切尔西"), "切尔西"));
        assertEquals("pn=2", SearchPages.withPage("", 2));
    }
}
