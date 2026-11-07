package com.zhiquan.sport.api.speak;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityUploadTest {

    @Test
    void jpegAndMp4AreAccepted() {
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
        CommunityUpload.File image = CommunityUpload.read(Base64.getEncoder().encodeToString(jpeg));
        assertFalse(image.video());
        assertEquals(".jpg", image.extension());
        byte[] mp4 = new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};
        CommunityUpload.File video = CommunityUpload.read("data:video/mp4;base64," + Base64.getEncoder().encodeToString(mp4));
        assertTrue(video.video());
        assertEquals("video/mp4", video.contentType());
        assertThrows(IllegalArgumentException.class, () -> CommunityUpload.read("data:image/png;base64,aaaa"));
    }
}
