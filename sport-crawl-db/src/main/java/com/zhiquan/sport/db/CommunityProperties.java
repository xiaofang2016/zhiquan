package com.zhiquan.sport.db;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sport.community")
public class CommunityProperties {

    private boolean enabled = true;
    private String baseUrl = "https://kzb001.net";
    private int pages = 3;
    private int pageSize = 20;
    private int commentPageSize = 50;
    private long requestIntervalMs = 200;
}
