package com.zhiquan.sport.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommunityDurationTest {

    @Test
    void sumsSegmentDurationsAndFindsVariant() {
        assertEquals(9, CommunityDuration.seconds("#EXTM3U\n#EXTINF:4.2,\na.ts\n#EXTINF:4.8,\nb.ts\n"));
        assertNull(CommunityDuration.seconds("#EXTM3U\n"));
        assertEquals("720.m3u8", CommunityDuration.variant("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n720.m3u8\n"));
        assertNull(CommunityDuration.variant("#EXTM3U\n#EXTINF:1,\na.ts\n"));
    }
}
