package com.zhiquan.sport.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class CrawlGate {

    private final ReentrantLock lock = new ReentrantLock(true);
    private final CrawlStatus status;

    public CrawlGate(CrawlStatus status) {
        this.status = status;
    }

    public void awaitRun(String phase, Runnable job) {
        lock.lock();
        try {
            runLocked(phase, job);
        } finally {
            lock.unlock();
        }
    }

    private void runLocked(String phase, Runnable job) {
        status.begin(phase);
        try {
            job.run();
            status.success(phase);
        } catch (Exception ex) {
            status.fail(phase, ex);
            log.error("crawl {} failed", phase, ex);
        }
    }
}
