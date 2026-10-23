package com.zhiquan.sport.worker;

import java.net.InetAddress;
import java.net.URI;

public final class ImageUrlPolicy {

    private ImageUrlPolicy() {
    }

    public static boolean allowed(String url) {
        if (url == null || url.length() > 2000) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            InetAddress addr = InetAddress.getByName(host);
            return !addr.isAnyLocalAddress()
                    && !addr.isLoopbackAddress()
                    && !addr.isLinkLocalAddress()
                    && !addr.isSiteLocalAddress();
        } catch (Exception ex) {
            return false;
        }
    }
}
