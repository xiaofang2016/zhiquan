package com.zhiquan.sport.worker;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CrawlStatus {

    private volatile boolean running;
    private volatile boolean backfillFinished;
    private volatile String phase = "idle";
    private volatile String currentDate = "";
    private volatile int page;
    private volatile int pageTotal;
    private volatile String lastError = "";
    private volatile String lastSuccessPhase = "";
    private volatile Instant lastSuccessAt;
    private final AtomicInteger matchesUpserted = new AtomicInteger();
    private final AtomicInteger imagesDownloaded = new AtomicInteger();
    private final AtomicInteger imagesSkipped = new AtomicInteger();
    private final AtomicInteger imagesFailed = new AtomicInteger();

    public synchronized void begin(String phase) {
        this.running = true;
        this.phase = phase;
        this.page = 0;
        this.pageTotal = 0;
    }

    public void page(LocalDate date, int page, int pageTotal) {
        this.currentDate = date.toString();
        this.page = page;
        this.pageTotal = pageTotal;
    }

    public synchronized void success(String phase) {
        this.running = false;
        this.phase = "idle";
        this.lastSuccessPhase = phase;
        this.lastSuccessAt = Instant.now();
        this.lastError = "";
    }

    public synchronized void fail(String phase, Exception ex) {
        this.running = false;
        this.phase = "idle";
        this.lastError = phase + ": " + ex.getMessage();
    }

    public void markBackfillFinished() {
        this.backfillFinished = true;
    }

    public boolean isBackfillFinished() {
        return backfillFinished;
    }

    public void addMatch() {
        matchesUpserted.incrementAndGet();
    }

    public void addImageDownloaded() {
        imagesDownloaded.incrementAndGet();
    }

    public void addImageSkipped() {
        imagesSkipped.incrementAndGet();
    }

    public void addImageFailed() {
        imagesFailed.incrementAndGet();
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("running", running);
        view.put("backfillFinished", backfillFinished);
        view.put("phase", phase);
        view.put("currentDate", currentDate);
        view.put("page", page);
        view.put("pageTotal", pageTotal);
        view.put("lastError", lastError);
        view.put("lastSuccessPhase", lastSuccessPhase);
        view.put("lastSuccessAt", lastSuccessAt == null ? "" : lastSuccessAt.toString());
        view.put("matchesUpserted", matchesUpserted.get());
        view.put("imagesDownloaded", imagesDownloaded.get());
        view.put("imagesSkipped", imagesSkipped.get());
        view.put("imagesFailed", imagesFailed.get());
        return view;
    }
}
