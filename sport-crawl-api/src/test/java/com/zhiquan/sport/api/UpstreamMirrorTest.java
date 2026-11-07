package com.zhiquan.sport.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamMirrorTest {

    @Test
    void mirrorListSkipsTheDatabase() {
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/gift/getGiftList"));
        assertTrue(UpstreamMirrorController.PATHS.contains("/prod-api/at/list"));
        assertFalse(UpstreamMirrorController.PATHS.contains("/prod-api/database/home"));
        assertFalse(UpstreamMirrorController.PATHS.contains("/prod-api/team/getMatchList"));
    }
}
