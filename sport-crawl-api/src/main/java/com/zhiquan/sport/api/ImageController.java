package com.zhiquan.sport.api;

import com.zhiquan.sport.store.entity.SportImage;
import com.zhiquan.sport.store.repo.S3ImageStore;
import com.zhiquan.sport.store.repo.SportImageRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImageController {

    private final SportImageRepository imageRepository;
    private final S3ImageStore imageStore;

    public ImageController(SportImageRepository imageRepository, S3ImageStore imageStore) {
        this.imageRepository = imageRepository;
        this.imageStore = imageStore;
    }

    @GetMapping("/api/sport/images/{hash:[a-f0-9]{64}}")
    public ResponseEntity<byte[]> image(@PathVariable String hash) {
        SportImage image = imageRepository.selectByHash(hash);
        if (image == null || !"ok".equals(image.getStatus()) || image.getRelativePath() == null || image.getRelativePath().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        byte[] body;
        try {
            body = imageStore.get(image.getRelativePath());
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
        if (body == null || body.length == 0) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (image.getContentType() != null && !image.getContentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(image.getContentType());
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        return ResponseEntity.ok().contentType(mediaType).body(body);
    }
}
