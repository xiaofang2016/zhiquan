package com.zhiquan.sport.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamFailureTest {

    @Test
    void missingParameterIsNotRetried() {
        assertTrue(UpstreamFailure.giveUp(
                "upstream 400 缺少必传请求参数 Required int parameter 'type' is not present"));
        assertTrue(UpstreamFailure.giveUp("HTTP 404"));
        assertFalse(UpstreamFailure.giveUp("upstream json 12"));
        assertFalse(UpstreamFailure.giveUp("HTTP 500"));
        assertFalse(UpstreamFailure.giveUp(null));
    }
}
