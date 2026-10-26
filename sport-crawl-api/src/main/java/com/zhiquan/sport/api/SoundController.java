package com.zhiquan.sport.api;

import com.zhiquan.sport.store.repo.S3ImageStore;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Set;

@RestController
public class SoundController {

    static final Set<String> NAMES = Set.of("default", "whistle", "drum", "broadcast", "horn", "victory");

    private final S3ImageStore images;

    public SoundController(S3ImageStore images) {
        this.images = images;
    }

    @GetMapping("/prod-api/match/sound/{name:[a-z]+}")
    public ResponseEntity<byte[]> sound(@PathVariable String name) {
        if (!NAMES.contains(name)) {
            return ResponseEntity.notFound().build();
        }
        byte[] body;
        try {
            body = images.get("sounds/" + name + ".mp3");
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
        if (body == null || body.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(body);
    }
}
