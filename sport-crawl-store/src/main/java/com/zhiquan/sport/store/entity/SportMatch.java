package com.zhiquan.sport.store.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SportMatch {

    private Long sourceId;
    private Integer cid;
    private Integer sportType;
    private LocalDate listDate;
    private LocalDateTime matchTime;
    private Integer isHot;
    private String listSection;
    private Integer sortOrder;
    private String payload;
    private LocalDateTime updatedAt;
}
