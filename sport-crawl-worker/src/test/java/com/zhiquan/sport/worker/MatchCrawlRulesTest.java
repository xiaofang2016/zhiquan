package com.zhiquan.sport.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.MatchKeys;
import com.zhiquan.sport.store.aws.KeyToken;
import com.zhiquan.sport.store.entity.SportMatch;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchCrawlRulesTest {

    @Test
    void pastCrawlStopsAtOneWeek() {
        LocalDate today = LocalDate.parse("2026-10-23");
        var dates = java.util.List.of(
                today.minusDays(8),
                today.minusDays(7),
                today.minusDays(1),
                today,
                today.plusDays(20));
        assertEquals(java.util.List.of(today.minusDays(1), today.minusDays(7)),
                MatchCrawlService.pastWithin(dates, today, 7));
    }

    @Test
    void pageSizeIgnoredWhenServerTreatsPageAsOne() {
        assertTrue(MatchListPages.pageSizeIgnored(3553, 3553, 1, 200));
        assertFalse(MatchListPages.pageSizeIgnored(3553, 18, 200, 200));
    }

    @Test
    void firstPageUnderReportsTheList() {
        assertEquals(87, MatchListPages.laterPageCount(18, 87));
        assertEquals(80, MatchListPages.laterPageCount(80, 79));
    }

    @Test
    void sameMatchCanStayOnTwoDates() {
        assertEquals("2026-10-24#4558644", MatchKeys.itemKey(LocalDate.parse("2026-10-24"), 4558644));
        assertEquals("2026-10-25#4558644", MatchKeys.itemKey(LocalDate.parse("2026-10-25"), 4558644));
        assertEquals(4558644L, MatchKeys.matchId("2026-10-25#4558644"));
        assertEquals(4558644L, MatchKeys.matchId("4558644"));
    }

    @Test
    void pageCountFollowsShorterBody() {
        assertEquals(18, MatchListPages.pageCount(3553, 18, 200, 200, 500));
        assertEquals(178, MatchListPages.pageCount(3553, 18, 20, 200, 500));
        assertEquals(1, MatchListPages.pageCount(5, 1, 5, 200, 500));
        assertEquals(500, MatchListPages.pageCount(3553, 3553, 1, 1, 500));
    }

    @Test
    void blocksLoopbackImageHost() {
        assertFalse(ImageUrlPolicy.allowed("http://127.0.0.1/a.png"));
        assertFalse(ImageUrlPolicy.allowed("ftp://cdn.example.com/a.png"));
    }

    @Test
    void detectsPng() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertEquals("image/png", ImageMime.detect(png));
        assertEquals("png", ImageMime.extension("image/png"));
        assertNull(ImageMime.detect(new byte[]{1, 2, 3}));
    }

    @Test
    void parsesMatchAndImageFields() throws Exception {
        String json = """
                {"id":4558644,"cid":82,"type":1,"isHot":1,"matchtime":"2026-10-24 03:00:00",
                 "clogo":"https://cdn.example.com/c.png","hteam_logo":"https://cdn.example.com/h.png",
                 "ateam_logo":"http://cdn.example.com/a.jpg","live_urls":[{"url":"https://example.com/a.flv"}]}
                """;
        var node = new ObjectMapper().readTree(json);
        SportMatch row = new MatchRowParser(new ObjectMapper()).toRow(node, LocalDate.parse("2026-10-23"), "data", 1, 0);
        assertEquals(4558644L, row.getSourceId());
        assertEquals(82, row.getCid());
        assertEquals(1, row.getSportType());
        assertEquals(1, row.getIsHot());
        assertEquals(100000, row.getSortOrder());
        assertTrue(row.getPayload().contains("a.flv"));
        assertEquals(3, MatchRowParser.imageUrls(node).size());
    }

    @Test
    void listKeyPadsSortBeforeSourceId() {
        String low = MatchKeys.listKey("data", 2, 9);
        String high = MatchKeys.listKey("data", 10, 1);
        assertEquals("data#0000000002#000000000000000009", low);
        assertTrue(low.compareTo(high) < 0);
        assertEquals("2026-10-23#82", MatchKeys.dateCid(LocalDate.parse("2026-10-23"), 82));
    }

    @Test
    void keyTokenRoundTrip() {
        var key = java.util.Map.of(
                "list_date", AttributeValue.fromS("2026-10-23"),
                "list_key", AttributeValue.fromS("data#0000100000#0000000000004558644"),
                "source_id", AttributeValue.fromS("4558644"));
        var decoded = KeyToken.decode(KeyToken.encode(key));
        assertEquals("2026-10-23", decoded.get("list_date").s());
        assertEquals("4558644", decoded.get("source_id").s());
        assertNull(KeyToken.decode(" "));
    }
}
