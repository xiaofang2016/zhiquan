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
        assertTrue(UpstreamFailure.giveUp("404 Not Found: \"{\\\"status\\\":404}\""));
        assertTrue(UpstreamFailure.giveUp("upstream -1 system error"));
        assertFalse(UpstreamFailure.giveUp("upstream json 12"));
        assertFalse(UpstreamFailure.giveUp("upstream -10 later"));
        assertFalse(UpstreamFailure.giveUp("HTTP 500"));
        assertFalse(UpstreamFailure.giveUp("500 Internal Server Error"));
        assertFalse(UpstreamFailure.giveUp(null));
    }
}
