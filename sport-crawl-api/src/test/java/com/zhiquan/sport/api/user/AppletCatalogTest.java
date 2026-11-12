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
        assertEquals("https://miniapp.mxzrbz.com/videoList", rows.get(1).get("appletUrl"));
        assertEquals("https://community.syjxyz.com/community/index", rows.get(3).get("appletUrl"));
        assertEquals("ued", rows.get(4).get("program"));
        assertEquals("jxt", rows.get(5).get("program"));
        assertEquals("群管晨曦专属", rows.get(8).get("appletTitle"));
        assertEquals("https://445d6dq.com/live-login", rows.get(8).get("appletUrl"));
        assertEquals("9", rows.get(8).get("idNum"));
        List<Map<String, String>> apk = AppletCatalog.list("apk");
        assertTrue(apk.stream().noneMatch(row -> "live".equals(row.get("appletId"))));
        assertEquals("ued专属网页", apk.get(7).get("appletTitle"));
    }
}
