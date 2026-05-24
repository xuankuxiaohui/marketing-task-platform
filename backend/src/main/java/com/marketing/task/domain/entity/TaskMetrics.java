package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("task_metrics")
public class TaskMetrics {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private LocalDate metricDate;
    private Long views;
    private Long participants;
    private Long completions;
    private Long rewardSuccess;
    private Long rewardFailure;
    private Double avgFilterMs;
}
