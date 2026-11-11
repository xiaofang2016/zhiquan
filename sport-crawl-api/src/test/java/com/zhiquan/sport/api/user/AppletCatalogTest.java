package com.zhiquan.sport.api.user;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppletCatalogTest {

    @Test
    void namesOrderAndLiveTitleMatchTheOriginalSite() {
        List<Map<String, String>> rows = AppletCatalog.list("h5");
        assertEquals("说球帝直播", rows.get(0).get("appletName"));
        assertEquals("西甲赞助商，五大联赛，NBA，CBA 无延迟超高清观看", rows.get(0).get("appletTitle"));
        assertEquals("成人短剧", rows.get(2).get("appletName"));
        assertEquals("ued", rows.get(4).get("program"));
        assertEquals("jxt", rows.get(5).get("program"));
        assertEquals("9", rows.get(8).get("idNum"));
        assertTrue(AppletCatalog.list("apk").stream().noneMatch(row -> "live".equals(row.get("appletId"))));
    }
}
