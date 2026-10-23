package com.zhiquan.sport.worker;

public final class ImageMime {

    private ImageMime() {
    }

    public static String detect(byte[] body) {
        if (body == null) {
            return null;
        }
        if (body.length >= 8 && (body[0] & 0xFF) == 0x89 && body[1] == 0x50 && body[2] == 0x4E && body[3] == 0x47) {
            return "image/png";
        }
        if (body.length >= 3 && (body[0] & 0xFF) == 0xFF && (body[1] & 0xFF) == 0xD8 && (body[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (body.length >= 6 && body[0] == 'G' && body[1] == 'I' && body[2] == 'F') {
            return "image/gif";
        }
        if (body.length >= 12 && body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                && body[8] == 'W' && body[9] == 'E' && body[10] == 'B' && body[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    public static String extension(String mime) {
        return switch (mime) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> null;
        };
    }
}
