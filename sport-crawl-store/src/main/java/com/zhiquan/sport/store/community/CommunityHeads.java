package com.zhiquan.sport.store.community;

import com.zhiquan.sport.store.SourceHash;
import com.zhiquan.sport.store.entity.SportImage;
import com.zhiquan.sport.store.repo.S3ImageStore;
import com.zhiquan.sport.store.repo.SportImageRepository;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把头像拉到自己的图床，并缩到一边不超过 256。COS 和已经转存过的地址不动。
 */
public final class CommunityHeads {

    private static final String AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_EDGE = 256;
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int ROUND_BUDGET = 40;

    private final SportImageRepository images;
    private final S3ImageStore store;
    private final String publicBase;
    private final HttpClient http;
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private int used;

    public CommunityHeads(SportImageRepository images, S3ImageStore store, String publicBase) {
        this.images = images;
        this.store = store;
        this.publicBase = publicBase == null ? "" : publicBase.replaceAll("/+$", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public void beginRound() {
        used = 0;
    }

    public synchronized String place(String url) {
        if (url == null || url.isBlank() || "null".equals(url) || url.contains("myqcloud.com") || url.startsWith(publicBase + "/prod-api/community/images/")) {
            return url == null ? "" : url;
        }
        if (!publicHttp(url) || failed.contains(url) || used >= ROUND_BUDGET) {
            return url;
        }
        String hash = SourceHash.sha256(url);
        try {
            SportImage saved = images.selectByHash(hash);
            if (saved != null && "ok".equals(saved.getStatus()) && saved.getRelativePath() != null && !saved.getRelativePath().isBlank()) {
                return published(hash);
            }
            byte[] body = download(url);
            byte[] jpeg = jpeg(body, MAX_EDGE);
            if (jpeg == null || jpeg.length == 0) {
                failed.add(url);
                return url;
            }
            String relative = hash.substring(0, 2) + "/" + hash + ".jpg";
            store.put(relative, jpeg, "image/jpeg");
            SportImage row = new SportImage();
            row.setSourceUrl(url.length() > 2048 ? url.substring(0, 2048) : url);
            row.setSourceHash(hash);
            row.setContentType("image/jpeg");
            row.setByteSize(jpeg.length);
            row.setRelativePath(relative);
            row.setStatus("ok");
            images.upsert(row);
            used++;
            return published(hash);
        } catch (Exception ex) {
            failed.add(url);
            return url;
        }
    }

    private String published(String hash) {
        return publicBase + "/prod-api/community/images/" + hash;
    }

    private byte[] download(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", AGENT)
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body() == null || response.body().length == 0 || response.body().length > MAX_BYTES) {
            return null;
        }
        return response.body();
    }

    static byte[] jpeg(byte[] body, int maxEdge) {
        if (body == null || body.length == 0) {
            return null;
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(body));
        } catch (Exception ex) {
            return null;
        }
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
            return null;
        }
        double scale = Math.min(1d, maxEdge / (double) Math.max(image.getWidth(), image.getHeight()));
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(out, "jpg", bytes)) {
                return null;
            }
            return bytes.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }

    static boolean publicHttp(String url) {
        if (url.length() > 2000) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
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
