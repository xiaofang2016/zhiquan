package com.zhiquan.sport.db;

/** 从 HLS 播放列表加总时长。主列表只认第一条子列表。 */
public final class CommunityDuration {

    private CommunityDuration() {
    }

    public static Integer seconds(String playlist) {
        if (playlist == null || !playlist.contains("#EXTINF:")) {
            return null;
        }
        double sum = 0;
        boolean any = false;
        for (String line : playlist.split("\\r?\\n")) {
            String row = line.trim();
            if (!row.startsWith("#EXTINF:")) {
                continue;
            }
            String number = row.substring("#EXTINF:".length());
            int comma = number.indexOf(',');
            if (comma >= 0) {
                number = number.substring(0, comma);
            }
            try {
                sum += Double.parseDouble(number.trim());
                any = true;
            } catch (NumberFormatException ignored) {
                // 这一行不是时长
            }
        }
        if (!any) {
            return null;
        }
        return (int) Math.round(sum);
    }

    /** 没有分片时长时，返回第一条子播放列表地址。 */
    public static String variant(String playlist) {
        if (playlist == null || playlist.contains("#EXTINF:")) {
            return null;
        }
        for (String line : playlist.split("\\r?\\n")) {
            String row = line.trim();
            if (row.isEmpty() || row.startsWith("#")) {
                continue;
            }
            if (row.contains(".m3u8")) {
                return row;
            }
        }
        return null;
    }
}
