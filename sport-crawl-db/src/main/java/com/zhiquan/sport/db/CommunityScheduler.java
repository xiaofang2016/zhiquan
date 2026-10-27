package com.zhiquan.sport.db;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommunityScheduler {

    private final CommunityProperties properties;
    private final CommunityCrawlService crawl;

    public CommunityScheduler(CommunityProperties properties, CommunityCrawlService crawl) {
        this.properties = properties;
        this.crawl = crawl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        run();
    }

    @Scheduled(fixedRateString = "120000", initialDelayString = "120000")
    public void round() {
        run();
    }

    private void run() {
        if (properties.isEnabled()) {
            crawl.round();
        }
    }
}
