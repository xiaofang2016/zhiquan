package com.zhiquan.sport.api.speak;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityUploadTest {

    @Test
    void videoMustBeH264AndFitTheGateway() {
        CommunityUpload.File file = CommunityUpload.read(data(ftyp(24, "isom", "avc1")));
        assertTrue(file.video());
        assertEquals("video/mp4", file.contentType());
        assertThrows(IllegalArgumentException.class, () -> CommunityUpload.read(data(ftyp(24, "qt  ", "hev1"))));
        assertThrows(IllegalArgumentException.class, () -> CommunityUpload.read(data(ftyp(20, "hvc1"))));
    }

    private static String data(byte[] bytes) {
        return "data:video/mp4;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] ftyp(int size, String major, String... compatible) {
        byte[] bytes = new byte[size];
        bytes[3] = (byte) size;
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        write(bytes, 8, major);
        int offset = 16;
        for (String brand : compatible) {
            write(bytes, offset, brand);
            offset += 4;
        }
        return bytes;
    }

    private static void write(byte[] bytes, int offset, String brand) {
        for (int i = 0; i < 4; i++) {
            bytes[offset + i] = (byte) brand.charAt(i);
        }
    }
}
