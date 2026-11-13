package com.zhiquan.sport.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainRewritesTest {

    @Test
    void defaultPairIgnoresCaseAndKeepsTheWholeName() {
        DomainRewrites rewrites = new DomainRewrites((String) null);
        String text = rewrites.apply("去 JX8.com、jx8.COM，www.jx8.com 和 jx8.com.cn 不动，QiuMiShuo.com 换成 qiumishuo.cc，www.qiumishuo.com 不动");
        assertEquals("去 JX8.CC、JX8.CC，www.jx8.com 和 jx8.com.cn 不动，qiumishuo.cc 换成 qiumishuo.cc，www.qiumishuo.com 不动", text);
    }

    @Test
    void eachNameMapsToItsOwnTarget() {
        DomainRewrites rewrites = new DomainRewrites("""
                # 一对一行
                www.jx8.com=www.JX8.CC
                JX8.com=JX8.CC
                not a domain=JX8.CC
                JX8.com=JX8.NET
                """);
        assertEquals(List.of(
                Map.of("from", "www.jx8.com", "to", "www.JX8.CC"),
                Map.of("from", "JX8.com", "to", "JX8.NET")
        ), rewrites.rows());
        assertEquals("看 www.JX8.CC 和 https://JX8.NET/a", rewrites.apply("看 www.jx8.com 和 https://jx8.com/a"));
    }

    @Test
    void emptyListRewritesNothing() {
        DomainRewrites rewrites = new DomainRewrites("");
        byte[] body = "{\"name\":\"JX8.com\"}".getBytes(StandardCharsets.UTF_8);
        assertSame(body, rewrites.apply(body));
        assertTrue(rewrites.rows().isEmpty());
    }

    @Test
    void jsonIsTextAndMediaIsNot() {
        assertTrue(DomainRewrites.textual("application/json;charset=UTF-8"));
        assertTrue(DomainRewrites.textual("application/vnd.apple.mpegurl"));
        assertFalse(DomainRewrites.textual("image/jpeg"));
        assertFalse(DomainRewrites.textual("video/mp2t"));
        assertFalse(DomainRewrites.textual("audio/mpeg"));
        assertFalse(DomainRewrites.textual(null));
    }
}
