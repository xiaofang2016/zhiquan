package com.zhiquan.sport.api.user;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 首页小程序目录。图标由页面自己画，这里不给原站图。
 * 吉祥、UED、红包、商城不带外链，页面走现有入口。
 */
public final class AppletCatalog {

    private AppletCatalog() {
    }

    public static List<Map<String, String>> list(String client) {
        String want = client == null ? "" : client.trim().toLowerCase(Locale.ROOT);
        List<Map<String, String>> rows = new ArrayList<>();
        int index = 1;
        for (Item item : ITEMS) {
            if (!want.isEmpty() && !item.clients.contains(want)) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("appletId", item.id);
            row.put("appletName", item.name);
            row.put("appletTitle", title(item, want));
            row.put("appletImage", "");
            row.put("appletUrl", item.url);
            row.put("program", item.program);
            row.put("idNum", Integer.toString(index));
            rows.add(row);
            index++;
        }
        return rows;
    }

    private record Item(String id, String name, String title, String url, String program, String clients) {
    }

    private static final String LIVE_TITLE = "西甲赞助商，五大联赛，NBA，CBA 无延迟超高清观看";

    private static final List<Item> ITEMS = List.of(
            new Item("live", "说球帝直播", LIVE_TITLE, "https://kzb.7pgb88.com/", "", "ipa,h5"),
            new Item("movie", "H片", "H片", "https://miniapp.mxzrbz.com/videoList", "", "apk,ipa,h5"),
            new Item("drama", "成人短剧", "成人短剧", "https://miniapp.mxzrbz.com/movieList", "", "apk,ipa,h5"),
            new Item("community", "社球帝", "社球帝", "https://community.syjxyz.com/community/index", "", "apk,ipa,h5"),
            new Item("ued", "UED体育官方网站", "UED体育官方网站", "", "ued", "apk,ipa,h5"),
            new Item("jxt", "吉祥体育官方网站", "吉祥体育官方网站", "", "jxt", "apk,ipa,h5"),
            new Item("wallet", "活动红包", "活动红包", "", "wallet", "apk,ipa,h5"),
            new Item("mall", "红包商城", "红包商城", "", "mall", "apk,ipa,h5"),
            new Item("ued-page", "ued专属网页", "ued专属网页", "https://445d6dq.com/live-login", "", "apk,ipa,h5")
    );

    private static String title(Item item, String client) {
        if ("ued-page".equals(item.id) && ("ipa".equals(client) || "h5".equals(client))) {
            return "群管晨曦专属";
        }
        return item.title;
    }
}
