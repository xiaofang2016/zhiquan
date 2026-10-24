package com.zhiquan.sport.worker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sport.crawl")
public class CrawlProperties {

    private boolean enabled = true;
    private boolean backfillOnStart = true;
    private String baseUrl = "https://kzb001.net";
    private String zoneId = "Asia/Shanghai";
    private int zhuboType = 1;
    private int requestIntervalMs = 200;
    private int pageSize = 200;
    private int fallbackPageSize = 20;
    private int maxPages = 500;
    private int imageMaxBytes = 2 * 1024 * 1024;
}
