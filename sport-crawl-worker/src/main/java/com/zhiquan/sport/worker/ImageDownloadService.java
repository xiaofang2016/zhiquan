package com.zhiquan.sport.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhiquan.sport.store.SourceHash;
import com.zhiquan.sport.store.entity.SportImage;
import com.zhiquan.sport.store.repo.S3ImageStore;
import com.zhiquan.sport.store.repo.SportImageRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
public class ImageDownloadService {

    private final KzbClient client;
    private final CrawlProperties properties;
    private final SportImageRepository imageRepository;
    private final S3ImageStore imageStore;
    private final CrawlStatus status;
    private final Set<String> okCache = new HashSet<>();

    public ImageDownloadService(KzbClient client,
                                CrawlProperties properties,
                                SportImageRepository imageRepository,
                                S3ImageStore imageStore,
                                CrawlStatus status) {
        this.client = client;
        this.properties = properties;
        this.imageRepository = imageRepository;
        this.imageStore = imageStore;
        this.status = status;
    }

    @PostConstruct
    public void init() {
        reloadOk();
        log.info("image bucket ready, cached {}", okCache.size());
    }

    public void downloadNodes(Collection<JsonNode> nodes) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (JsonNode node : nodes) {
            urls.addAll(MatchRowParser.imageUrls(node));
        }
        for (String url : urls) {
            download(url);
        }
    }

    private void download(String url) {
        if (!ImageUrlPolicy.allowed(url)) {
            log.warn("skip image host {}", url);
            return;
        }
        String hash = SourceHash.sha256(url);
        if (okCache.contains(hash)) {
            status.addImageSkipped();
            return;
        }
        try {
            KzbClient.Downloaded downloaded = client.getBytes(URI.create(url), properties.getImageMaxBytes());
            String mime = ImageMime.detect(downloaded.body());
            String extension = mime == null ? null : ImageMime.extension(mime);
            if (extension == null || downloaded.body().length == 0) {
                saveFailure(url, hash, "not an image");
                return;
            }
            String relative = hash.substring(0, 2) + "/" + hash + "." + extension;
            imageStore.put(relative, downloaded.body(), mime);
            status.addImageDownloaded();
            SportImage row = new SportImage();
            row.setSourceUrl(url);
            row.setSourceHash(hash);
            row.setContentType(mime);
            row.setByteSize(downloaded.body().length);
            row.setRelativePath(relative);
            row.setStatus("ok");
            imageRepository.upsert(row);
            okCache.add(hash);
        } catch (Exception ex) {
            saveFailure(url, hash, ex.getMessage());
        }
    }

    private void saveFailure(String url, String hash, String message) {
        SportImage row = new SportImage();
        row.setSourceUrl(truncate(url, 2048));
        row.setSourceHash(hash);
        row.setContentType("");
        row.setByteSize(0);
        row.setRelativePath("");
        row.setStatus("failed");
        row.setErrorMessage(truncate(message == null ? "" : message, 250));
        imageRepository.upsert(row);
        okCache.remove(hash);
        status.addImageFailed();
        log.warn("image failed {} {}", url, message);
    }

    private void reloadOk() {
        okCache.clear();
        for (SportImage image : imageRepository.selectOk()) {
            if (image.getSourceHash() == null || image.getRelativePath() == null || image.getRelativePath().isBlank()) {
                continue;
            }
            okCache.add(image.getSourceHash());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
