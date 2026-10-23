package com.zhiquan.sport.db;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class DbScheduler {

    private final DbProperties properties;
    private final DatabaseCrawlService crawl;

    public DbScheduler(DbProperties properties, DatabaseCrawlService crawl) {
        this.properties = properties;
        this.crawl = crawl;
    }

    @Scheduled(cron = "0 30 4 * * ?", zone = "Asia/Shanghai")
    public void daily() {
        if (properties.isEnabled()) {
            crawl.daily();
        }
    }

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES, initialDelay = 3)
    public void live() {
        if (properties.isEnabled()) {
            crawl.live();
        }
    }

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS, initialDelay = 20)
    public void demand() {
        if (properties.isEnabled()) {
            crawl.demand();
        }
    }
}
