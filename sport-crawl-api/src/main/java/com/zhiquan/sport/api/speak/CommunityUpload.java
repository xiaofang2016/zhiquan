package com.zhiquan.sport.api.speak;

import java.util.Base64;

/** 社区上传收压好的 JPEG，或不再转码的 MP4。 */
public final class CommunityUpload {

    static final int MAX_IMAGE_BYTES = 200_000;
    static final int MAX_VIDEO_BYTES = 8_000_000;

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
                throw new IllegalArgumentException("视频请小于 8MB");
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
}
