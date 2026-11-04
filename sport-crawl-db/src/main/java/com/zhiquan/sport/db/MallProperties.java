package com.zhiquan.sport.db;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sport.mall")
public class MallProperties {

    private boolean enabled = true;
    private int pageSize = 50;
}
