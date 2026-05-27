package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task")
@Schema(description = "任务主体")
public class Task {
    @Schema(description = "任务ID")
    @TableId(type = IdType.AUTO)
    private Long id;
    @Schema(description = "任务编码")
    private String code;
    @Schema(description = "任务名称")
    private String name;
    @Schema(description = "任务描述")
    private String description;
    @Schema(description = "周期类型: ONCE/DAILY/MONTHLY/CRON/SPECIAL")
    private String periodType;
    @Schema(description = "Cron 表达式")
    private String cronExpr;
    @Schema(description = "特殊周期标识")
    private String specialCycleKey;
    @Schema(description = "开始时间")
    private LocalDateTime startTime;
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
    @Schema(description = "状态: DRAFT/PUBLISHED/OFFLINE/SCHEDULED")
    private String status;
    @Schema(description = "定时发布时间")
    private LocalDateTime scheduledPublishAt;
    @Schema(description = "版本号")
    private Integer version;
    @Schema(description = "互斥组ID")
    private Long mutexGroupId;
    @Schema(description = "灰度类型: NONE/PERCENTAGE/AB/CROWD")
    private String grayType;
    @Schema(description = "灰度配置 JSON")
    private String grayConfig;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
