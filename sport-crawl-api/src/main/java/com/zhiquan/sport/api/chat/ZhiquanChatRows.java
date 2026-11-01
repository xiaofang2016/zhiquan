package com.zhiquan.sport.api.chat;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.regex.Pattern;

/** 把 zhiquan_chat 的一行收成调试页要用的字段。 */
public final class ZhiquanChatRows {

    static final int MESSAGE_LIMIT = 100;
    private static final int CONTENT_LIMIT = 2000;
    private static final int NOTICE_LIMIT = 1024;
    private static final Pattern NOTICE_DISABLED = Pattern.compile("\"enabled\"\\s*:\\s*0");

    private ZhiquanChatRows() {
    }

    public record GroupRow(long id, String gid, String name, String ownerName, long updatedAt, String notice, String avatar, MessageRow latest) {
    }

    public record MessageRow(long time, String nick, String sendUid, String type, String content) {
    }

    public static GroupRow group(Map<String, AttributeValue> item) {
        String gid = text(item.get("gid"));
        if (gid.isEmpty()) {
            gid = gidFromSortKey(text(item.get("sk")));
        }
        Long id = parseLong(gid);
        if (id == null) {
            return null;
        }
        return new GroupRow(id, gid, text(item.get("name")), text(item.get("ownerName")),
                millis(number(item.get("updatedAt"))), notice(item), avatar(item), null);
    }

    /**
     * 原站群公告在 g_notice。enabled=0 时不展示。
     */
    static String notice(Map<String, AttributeValue> item) {
        Map<String, AttributeValue> raw = map(item.get("raw"));
        String notice = first(
                text(item.get("notice")),
                text(item.get("g_notice")),
                text(raw.get("g_notice")),
                text(raw.get("notice")));
        if (notice.isEmpty()) {
            return "";
        }
        String config = first(text(item.get("g_notice_show_config")), text(raw.get("g_notice_show_config")));
        if (NOTICE_DISABLED.matcher(config).find()) {
            return "";
        }
        return notice.length() <= NOTICE_LIMIT ? notice : notice.substring(0, NOTICE_LIMIT);
    }

    /** 原站群头像文件名在 group_avatar_file_name，偶发直接给 headUrl。 */
    static String avatar(Map<String, AttributeValue> item) {
        Map<String, AttributeValue> raw = map(item.get("raw"));
        return first(
                text(item.get("group_avatar_file_name")),
                text(raw.get("group_avatar_file_name")),
                text(item.get("headUrl")),
                text(raw.get("headUrl")));
    }

    public static MessageRow message(Map<String, AttributeValue> item) {
        long time = millis(number(item.get("time")));
        if (time <= 0) {
            time = millis(number(item.get("sendTime")));
        }
        String type = field(item.get("msgType"));
        if (type.isEmpty()) {
            type = field(item.get("type"));
        }
        String nick = text(item.get("nickName"));
        if (nick.isEmpty()) {
            nick = text(item.get("sendNickName"));
        }
        String sendUid = text(item.get("sendUid"));
        if (sendUid.isEmpty()) {
            sendUid = text(item.get("sendId"));
        }
        return new MessageRow(time, nick, sendUid, type, clip(text(item.get("content"))));
    }

    /** 原站点名「谁领了红包」不进聊天。人数汇总「等12/200人已领取了红包」保留。 */
    public static boolean isClaimNotice(String content) {
        if (content == null) {
            return false;
        }
        String text = content.trim();
        if (text.isEmpty() || text.contains("人已领取")) {
            return false;
        }
        return "领取了红包".equals(text) || "已领取了红包".equals(text) || text.endsWith("已领取了红包");
    }

    static String gidFromSortKey(String sk) {
        if (!sk.startsWith("group#")) {
            return "";
        }
        String rest = sk.substring("group#".length());
        int cut = rest.indexOf('#');
        return cut < 0 ? rest : rest.substring(0, cut);
    }

    private static String clip(String content) {
        return content.length() <= CONTENT_LIMIT ? content : content.substring(0, CONTENT_LIMIT);
    }

    private static long millis(Long raw) {
        if (raw == null || raw <= 0) {
            return 0;
        }
        return raw < 10_000_000_000L ? raw * 1000 : raw;
    }

    private static Long parseLong(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long number(AttributeValue value) {
        if (value == null || value.n() == null || value.n().isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.n().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Map<String, AttributeValue> map(AttributeValue value) {
        return value != null && value.hasM() ? value.m() : Map.of();
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String text(AttributeValue value) {
        if (value == null || value.s() == null) {
            return "";
        }
        return value.s();
    }

    /** 抓取侧把 msgType 存成数字，字符串和数字都要读出来。 */
    private static String field(AttributeValue value) {
        String text = text(value);
        if (!text.isBlank()) {
            return text.trim();
        }
        if (value == null || value.n() == null || value.n().isBlank()) {
            return "";
        }
        String number = value.n().trim();
        int dot = number.indexOf('.');
        return dot < 0 ? number : number.substring(0, dot);
    }
}
