package com.zhiquan.sport.db;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MallScheduler {

    private final MallProperties properties;
    private final MallCrawlService crawl;

    public MallScheduler(MallProperties properties, MallCrawlService crawl) {
        this.properties = properties;
        this.crawl = crawl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        run();
    }

    @Scheduled(fixedRateString = "300000", initialDelayString = "180000")
    public void round() {
        run();
    }

    private void run() {
        if (properties.isEnabled()) {
            crawl.round();
        }
    }
}
