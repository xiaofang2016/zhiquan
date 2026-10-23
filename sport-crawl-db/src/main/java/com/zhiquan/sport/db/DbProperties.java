package com.zhiquan.sport.db;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sport.database")
public class DbProperties {

    private boolean enabled = true;
    private String baseUrl = "https://kzb001.net";
    private String zoneId = "Asia/Shanghai";
    private int requestIntervalMs = 200;
    private int historyBudget = 400;
    private int historyScanCap = 2000;
}
