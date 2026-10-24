package com.zhiquan.sport.api;

import com.zhiquan.sport.store.entity.SportMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ListRevisionTest {

    @Test
    void revisionStaysPutUntilThePageChanges() {
        List<SportMatch> top = List.of(row(1, 0, "{\"id\":1}"));
        List<SportMatch> data = List.of(row(2, 1, "{\"id\":2,\"score\":\"0-0\"}"));
        String first = ProdApiService.revision(2, 1, List.of(), top, data);
        assertEquals(first, ProdApiService.revision(2, 1, List.of(), top, data));
        assertNotEquals(first, ProdApiService.revision(3, 1, List.of(), top, data));
        assertNotEquals(first, ProdApiService.revision(2, 1, List.of(), top, List.of(row(2, 1, "{\"id\":2,\"score\":\"1-0\"}"))));
        assertNotEquals(first, ProdApiService.revision(2, 1, List.of(), List.of(row(9, 0, "{\"id\":9}")), data));
    }

    private static SportMatch row(long id, int order, String payload) {
        SportMatch row = new SportMatch();
        row.setSourceId(id);
        row.setSortOrder(order);
        row.setPayload(payload);
        return row;
    }
}
