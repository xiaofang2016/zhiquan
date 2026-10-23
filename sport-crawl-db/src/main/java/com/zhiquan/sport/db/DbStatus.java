package com.zhiquan.sport.db;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DbStatus {

    private volatile boolean running;
    private volatile String phase = "idle";
    private volatile String lastError = "";
    private volatile String lastSuccessPhase = "";
    private volatile Instant lastSuccessAt;
    private final AtomicInteger fetched = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    public synchronized void begin(String phase) {
        this.running = true;
        this.phase = phase;
        this.fetched.set(0);
        this.skipped.set(0);
        this.failed.set(0);
    }

    public void fetched() {
        fetched.incrementAndGet();
    }

    public void skipped() {
        skipped.incrementAndGet();
    }

    public void failed() {
        failed.incrementAndGet();
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

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("running", running);
        view.put("phase", phase);
        view.put("fetched", fetched.get());
        view.put("skipped", skipped.get());
        view.put("failed", failed.get());
        view.put("lastError", lastError);
        view.put("lastSuccessPhase", lastSuccessPhase);
        view.put("lastSuccessAt", lastSuccessAt == null ? "" : lastSuccessAt.toString());
        return view;
    }
}
