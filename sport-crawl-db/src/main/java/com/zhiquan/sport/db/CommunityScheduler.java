package com.zhiquan.sport.db;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CommunityScheduler {

    private final CommunityProperties properties;
    private final CommunityCrawlService crawl;

    public CommunityScheduler(CommunityProperties properties, CommunityCrawlService crawl) {
        this.properties = properties;
        this.crawl = crawl;
    }

    @Scheduled(fixedRate = 2, timeUnit = TimeUnit.MINUTES, initialDelay = 25)
    public void round() {
        if (properties.isEnabled()) {
            crawl.round();
        }
    }
}
