package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchHistoryTest {

    @Test
    void localHistoryKeepsAccountWords() throws Exception {
        ObjectMapper json = new ObjectMapper();
        SearchHistory history = new SearchHistory(null, null, json);
        JsonNode body = json.readTree(history.local(List.of("英超", "湖人")));
        assertEquals("0", body.path("code").asText());
        assertEquals(0, body.path("data").path("hotSearchHistory").size());
        assertEquals("英超", body.path("data").path("userSearchHistory").get(0).path("key").asText());
        assertEquals("湖人", body.path("data").path("userSearchHistory").get(1).path("key").asText());
    }
}
