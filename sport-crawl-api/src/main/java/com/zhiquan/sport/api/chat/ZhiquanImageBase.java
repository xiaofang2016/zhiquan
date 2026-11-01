package com.zhiquan.sport.api.chat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 原站图片域名会换。1018/28/40 返回当前资源根地址。 */
public final class ZhiquanImageBase {

    static final String FALLBACK = "https://oss.imnono.net";
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'\\\\<>]+");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ZhiquanImageBase() {
    }

    public static String fetch(String imBase) {
        if (imBase == null || imBase.isBlank()) {
            return "";
        }
        String root = imBase.trim().replaceAll("/+$", "");
        String payload = "{\"processorId\":1018,\"device\":0,\"doInput\":true,\"jobDispatchId\":28,\"actionId\":40}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(root + "/rest_post"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return fromResponse(response.body());
        } catch (Exception ignored) {
            return "";
        }
    }

    static String fromResponse(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        int at = body.indexOf("returnValue");
        String slice = at >= 0 ? body.substring(at) : body;
        Matcher matcher = URL.matcher(slice);
        if (!matcher.find()) {
            return "";
        }
        String url = matcher.group();
        while (url.endsWith("/") || url.endsWith("\\")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
