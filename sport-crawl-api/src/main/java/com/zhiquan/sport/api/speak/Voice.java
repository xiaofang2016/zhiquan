package com.zhiquan.sport.api.speak;

public final class Voice {

    private Voice() {
    }

    public static String nick(String raw) {
        String nick = trim(raw);
        if (nick.isEmpty() || nick.length() > 20) {
            throw new IllegalArgumentException("昵称应为 1 到 20 个字");
        }
        return nick;
    }

    public static String chat(String raw) {
        String content = trim(raw);
        if (content.isEmpty() || content.length() > 200) {
            throw new IllegalArgumentException("发言应为 1 到 200 个字");
        }
        return content;
    }

    public static String title(String raw) {
        String title = trim(raw);
        if (title.length() > 40) {
            throw new IllegalArgumentException("标题不能超过 40 个字");
        }
        return title;
    }

    public static String post(String raw) {
        String content = trim(raw);
        if (content.isEmpty() || content.length() > 500) {
            throw new IllegalArgumentException("正文应为 1 到 500 个字");
        }
        return content;
    }

    public static String comment(String raw) {
        String content = trim(raw);
        if (content.isEmpty() || content.length() > 200) {
            throw new IllegalArgumentException("回复应为 1 到 200 个字");
        }
        return content;
    }

    public static String head(String raw) {
        String head = trim(raw);
        if (head.isEmpty()) {
            return "";
        }
        if (head.length() > 300 || !(head.startsWith("https://") || head.startsWith("http://") || head.startsWith("/"))) {
            throw new IllegalArgumentException("头像地址不正确");
        }
        return head;
    }

    public static String badge(String raw) {
        String badge = trim(raw);
        if (badge.length() > 20) {
            throw new IllegalArgumentException("徽章不能超过 20 个字");
        }
        return badge;
    }

    public static String postId(String raw) {
        String id = trim(raw);
        if (!id.matches("[0-9]{1,18}")) {
            throw new IllegalArgumentException("帖子编号不正确");
        }
        return id;
    }

    private static String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
