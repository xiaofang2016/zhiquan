package com.zhiquan.sport.api.user;

import java.util.Base64;

/**
 * 头像只收前端压好的 JPEG。原站说明是小于 2MB 的 jpg、png，页面会先压成 JPEG 再提交。
 * 文件放在图片桶的 avatar/ 下，账号里只留读取地址。
 */
public final class AvatarImage {

    static final int MAX_BYTES = 200_000;

    private AvatarImage() {
    }

    public static String objectKey(String userId) {
        return "avatar/" + userId + ".jpg";
    }

    public static String path(String userId, long version) {
        return "/prod-api/user/avatar/" + userId + "?v=" + version;
    }

    public static byte[] jpeg(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        String data = raw.trim();
        if (data.startsWith("data:")) {
            int comma = data.indexOf(',');
            String header = comma >= 0 ? data.substring(0, comma) : data;
            if (!header.startsWith("data:image/jpeg") && !header.startsWith("data:image/jpg")) {
                throw new IllegalArgumentException("请选择图片文件");
            }
            data = comma >= 0 ? data.substring(comma + 1) : "";
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("文件无效");
        }
        if (bytes.length < 3 || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("大小小于2MB，可以用jpg，png等格式～");
        }
        return bytes;
    }
}
