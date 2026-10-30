package com.zhiquan.sport.api.speak;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 把原站视频清单改到网关上，浏览器才能跨域播放。 */
public final class CommunityVideo {

    private static final Pattern TASK = Pattern.compile("https?://[^/]+/comment-api/video/hls/m3u8\\?taskId=([0-9]{1,12})");
    private static final String FILE_HOST = "commentfile.yc12315.com";

    private CommunityVideo() {
    }

    public static String playable(String url, String publicBase) {
        if (url == null || url.isBlank() || publicBase == null || publicBase.isBlank()) {
            return url == null ? "" : url;
        }
        Matcher matcher = TASK.matcher(url.trim());
        if (!matcher.matches()) {
            return url;
        }
        return publicBase + "/prod-api/community/video/" + matcher.group(1) + "/index.m3u8";
    }

    public static String rewrite(String playlist, String taskId, String publicBase) {
        String key = publicBase + "/prod-api/community/video/" + taskId + "/key";
        StringBuilder out = new StringBuilder();
        for (String line : playlist.split("\\r?\\n", -1)) {
            String row = line.trim();
            if (row.startsWith("#EXT-X-KEY:")) {
                out.append(row.replaceAll("URI=\"[^\"]*\"", "URI=\"" + key + "\"")).append('\n');
                continue;
            }
            if ((row.startsWith("https://") || row.startsWith("http://")) && allowed(row)) {
                out.append(publicBase)
                        .append("/prod-api/community/video/media?src=")
                        .append(URLEncoder.encode(row, StandardCharsets.UTF_8))
                        .append('\n');
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * @return 只接受原站视频文件域名上的分片
     */
    public static URI segment(String src) {
        URI uri;
        try {
            uri = URI.create(src == null ? "" : src.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("视频地址不正确");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !FILE_HOST.equalsIgnoreCase(uri.getHost()) || !path.startsWith("/community/video/")) {
            throw new IllegalArgumentException("视频地址不正确");
        }
        return uri;
    }

    private static boolean allowed(String url) {
        try {
            segment(url);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
