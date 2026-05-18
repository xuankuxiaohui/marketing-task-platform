package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String periodType;
    private String cronExpr;
    private String specialCycleKey;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer version;
    private String mutexGroupKey;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
