package com.zhiquan.sport.store.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SportMatchDate {

    private LocalDate matchDate;
    private Integer totalMatch;
    private LocalDateTime updatedAt;
}
