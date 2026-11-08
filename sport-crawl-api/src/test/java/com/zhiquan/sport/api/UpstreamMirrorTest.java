package com.zhiquan.sport.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamMirrorTest {

    @Test
    void mirrorListSkipsTheDatabase() {
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/gift/getGiftList"));
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/at/list"));
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/search/hot"));
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/search/content"));
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/data/lanqiu/ranklist"));
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/match/detail/player"));
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/match/news/information/related/list"));
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/news/list"));
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/news/detail"));
        assertFalse(UpstreamMirrorController.PATHS.contains("/prod-api/database/home"));
        assertFalse(UpstreamMirrorController.PATHS.contains("/prod-api/team/getMatchList"));
    }
}
