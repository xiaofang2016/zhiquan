package com.zhiquan.sport.db;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sport.community")
public class CommunityProperties {

    private boolean enabled = true;
    private String baseUrl = "https://kzb001.net";
    private String publicBase = "https://dgtplaoir00la.cloudfront.net";
    private int pages = 20;
    private int pageSize = 20;
    private int essencePages = 3;
    private int circlePages = 2;
    private int commentPageSize = 50;
    private int commentPages = 10;
    private long requestIntervalMs = 200;
}
