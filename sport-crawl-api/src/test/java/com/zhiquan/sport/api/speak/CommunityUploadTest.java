package com.zhiquan.sport.api.speak;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityUploadTest {

    @Test
    void videoMustBeH264AndFitTheGateway() {
        CommunityUpload.File file = CommunityUpload.read(data(mp4("avc1")));
        assertTrue(file.video());
        assertEquals("video/mp4", file.contentType());
        assertThrows(IllegalArgumentException.class, () -> CommunityUpload.read(data(ftyp(24, "qt  ", "hev1"))));
        assertThrows(IllegalArgumentException.class, () -> CommunityUpload.read(data(ftyp(20, "hvc1"))));
        assertThrows(IllegalArgumentException.class, () -> CommunityUpload.read(data(mp4("hev1"))));
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

    /** ftyp 写成 isom，编码只出现在 stsd。 */
    private static byte[] mp4(String codec) {
        byte[] head = ftyp(20, "isom");
        byte[] sample = box(codec, new byte[8]);
        byte[] table = new byte[8 + sample.length];
        table[7] = 1;
        System.arraycopy(sample, 0, table, 8, sample.length);
        byte[] moov = box("moov", box("trak", box("mdia", box("minf", box("stbl", box("stsd", table))))));
        byte[] bytes = new byte[head.length + moov.length];
        System.arraycopy(head, 0, bytes, 0, head.length);
        System.arraycopy(moov, 0, bytes, head.length, moov.length);
        return bytes;
    }

    private static byte[] box(String type, byte[] payload) {
        byte[] bytes = new byte[8 + payload.length];
        int size = bytes.length;
        bytes[0] = (byte) (size >>> 24);
        bytes[1] = (byte) (size >>> 16);
        bytes[2] = (byte) (size >>> 8);
        bytes[3] = (byte) size;
        write(bytes, 4, type);
        System.arraycopy(payload, 0, bytes, 8, payload.length);
        return bytes;
    }
}
