package com.zhiquan.sport.api.speak;

import java.util.Base64;

/** 社区上传收压好的 JPEG，或不再转码的 MP4。 */
public final class CommunityUpload {

    static final int MAX_IMAGE_BYTES = 200_000;
    /** 网关请求体大约 10MB。base64 后再留出 JSON，原始视频停在 7MB。 */
    static final int MAX_VIDEO_BYTES = 7_000_000;

    private CommunityUpload() {
    }

    public record File(byte[] bytes, String contentType, String extension, boolean video) {
    }

    public static File read(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("请选择文件");
        }
        String data = raw.trim();
        boolean video = false;
        if (data.startsWith("data:")) {
            int comma = data.indexOf(',');
            String header = comma >= 0 ? data.substring(0, comma) : data;
            video = header.startsWith("data:video/mp4");
            if (!video && !header.startsWith("data:image/jpeg") && !header.startsWith("data:image/jpg")) {
                throw new IllegalArgumentException("请选择图片或 MP4");
            }
            data = comma >= 0 ? data.substring(comma + 1) : "";
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("文件无效");
        }
        if (video || isMp4(bytes)) {
            if (!isMp4(bytes)) {
                throw new IllegalArgumentException("请选择图片或 MP4");
            }
            if (bytes.length > MAX_VIDEO_BYTES) {
                throw new IllegalArgumentException("视频请小于 7MB");
            }
            if (!h264(bytes)) {
                throw new IllegalArgumentException("请上传 H.264 的 MP4");
            }
            return new File(bytes, "video/mp4", ".mp4", true);
        }
        if (bytes.length < 3 || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8) {
            throw new IllegalArgumentException("请选择图片或 MP4");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("大小小于2MB，可以用jpg，png等格式～");
        }
        return new File(bytes, "image/jpeg", ".jpg", false);
    }

    private static boolean isMp4(byte[] bytes) {
        return bytes.length >= 8
                && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
    }

    /** ftyp 里出现 hev1 或 hvc1 就是 HEVC，安卓和浏览器放不了。 */
    static boolean h264(byte[] bytes) {
        if (!isMp4(bytes)) {
            return false;
        }
        int size = readInt(bytes);
        if (size < 16 || size > bytes.length) {
            size = Math.min(bytes.length, 32);
        }
        if (hevc(brand(bytes, 8))) {
            return false;
        }
        for (int offset = 16; offset + 4 <= size; offset += 4) {
            if (hevc(brand(bytes, offset))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hevc(String brand) {
        return "hev1".equals(brand) || "hvc1".equals(brand);
    }

    private static String brand(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int readInt(byte[] bytes) {
        return ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
    }
}
