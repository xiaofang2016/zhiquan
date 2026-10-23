package com.zhiquan.sport.db;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DbController {

    private final DbStatus status;
    private final DatabaseCrawlService crawl;

    public DbController(DbStatus status, DatabaseCrawlService crawl) {
        this.status = status;
        this.crawl = crawl;
    }

    @GetMapping("/crawl/status")
    public Map<String, Object> status() {
        return status.snapshot();
    }

    @PostMapping("/crawl/run")
    public Map<String, Object> run(@RequestParam(defaultValue = "daily") String scope) {
        Thread thread = new Thread(() -> {
            if ("live".equals(scope)) {
                crawl.live();
            } else {
                crawl.daily();
            }
        }, "db-manual");
        thread.setDaemon(true);
        thread.start();
        return Map.of("accepted", true, "scope", scope);
    }
}
