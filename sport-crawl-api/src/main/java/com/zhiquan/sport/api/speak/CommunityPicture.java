package com.zhiquan.sport.api.speak;

/** 从 JPEG 里读宽高。读不到时是 0。 */
public final class CommunityPicture {

    private CommunityPicture() {
    }

    public static int[] size(byte[] data) {
        if (data == null) {
            return new int[] {0, 0};
        }
        int i = 2;
        while (i + 8 < data.length) {
            if ((data[i] & 0xff) != 0xff) {
                break;
            }
            int marker = data[i + 1] & 0xff;
            if (marker == 0xc0 || marker == 0xc1 || marker == 0xc2) {
                int height = ((data[i + 5] & 0xff) << 8) | (data[i + 6] & 0xff);
                int width = ((data[i + 7] & 0xff) << 8) | (data[i + 8] & 0xff);
                return new int[] {width, height};
            }
            if (marker == 0xd8 || marker == 0x01) {
                i += 2;
                continue;
            }
            if (i + 3 >= data.length) {
                break;
            }
            int length = ((data[i + 2] & 0xff) << 8) | (data[i + 3] & 0xff);
            if (length < 2) {
                break;
            }
            i += 2 + length;
        }
        return new int[] {0, 0};
    }
}
