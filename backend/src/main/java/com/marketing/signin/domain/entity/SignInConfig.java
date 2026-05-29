package com.marketing.signin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("signin_config")
public class SignInConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String status;
    private String periodType;
    private Integer basePoints;
    private String streakConfig;
    private Integer pointExpireDays;
    private Boolean catchUpEnabled;
    private Integer catchUpCost;
    private Integer catchUpMaxDays;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String activityCode;
}
