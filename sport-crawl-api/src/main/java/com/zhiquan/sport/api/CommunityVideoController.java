package com.zhiquan.sport.api;

import com.zhiquan.sport.api.speak.CommunitySource;
import com.zhiquan.sport.api.speak.CommunityVideo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class CommunityVideoController {

    private final CommunitySource source;
    private final String publicBase;

    public CommunityVideoController(CommunitySource source,
                                    @Value("${sport.community.public-base:https://dgtplaoir00la.cloudfront.net}") String publicBase) {
        this.source = source;
        this.publicBase = publicBase.endsWith("/") ? publicBase.substring(0, publicBase.length() - 1) : publicBase;
    }

    @GetMapping("/prod-api/community/video/{taskId:[0-9]{1,12}}/index.m3u8")
    public ResponseEntity<byte[]> index(@PathVariable String taskId) {
        String playlist = CommunityVideo.rewrite(source.playlist(taskId), taskId, publicBase);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .body(playlist.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/prod-api/community/video/{taskId:[0-9]{1,12}}/key")
    public ResponseEntity<byte[]> key(@PathVariable String taskId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(source.videoKey(taskId));
    }

    @GetMapping("/prod-api/community/video/media")
    public ResponseEntity<byte[]> media(@RequestParam String src) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp2t"))
                .body(source.videoFile(CommunityVideo.segment(src)));
    }
}
