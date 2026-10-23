package com.zhiquan.sport.worker;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CrawlController {

    private final MatchCrawlScheduler scheduler;
    private final CrawlStatus status;

    public CrawlController(MatchCrawlScheduler scheduler, CrawlStatus status) {
        this.scheduler = scheduler;
        this.status = status;
    }

    @GetMapping("/crawl/status")
    public Map<String, Object> status() {
        return status.snapshot();
    }

    @PostMapping("/crawl/run")
    public Map<String, Object> run(@RequestParam(defaultValue = "today") String scope) {
        Thread thread = new Thread(() -> {
            if ("backfill".equals(scope)) {
                scheduler.backfill();
            } else {
                scheduler.today();
            }
        }, "sport-crawl-manual");
        thread.setDaemon(true);
        thread.start();
        return Map.of("accepted", true, "scope", scope);
    }
}
