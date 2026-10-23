package com.zhiquan.sport.store.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SportLeague {

    private Integer sourceId;
    private Integer sportType;
    private Integer sortOrder;
    private String payload;
    private LocalDateTime updatedAt;
}
