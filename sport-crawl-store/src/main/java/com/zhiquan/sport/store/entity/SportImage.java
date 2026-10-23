package com.zhiquan.sport.store.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SportImage {

    private String sourceUrl;
    private String sourceHash;
    private String contentType;
    private Integer byteSize;
    private String relativePath;
    private String status;
    private String errorMessage;
    private LocalDateTime updatedAt;
}
