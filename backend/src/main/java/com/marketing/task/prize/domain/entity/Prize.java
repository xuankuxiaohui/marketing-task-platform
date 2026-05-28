package com.marketing.task.prize.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prize")
public class Prize {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String name;
    private String description;
    private String handlerBean;
    private String paramsJson;
    private Integer totalStock;
    private Integer monthlyStock;
    private Integer dailyStock;
    private Integer userTotalLimit;
    private Integer userMonthlyLimit;
    private Integer userDailyLimit;
    private String limitsJson;
    private Long activityId;
    private String groupKey;
    private String groupStrategy;
    private Integer groupWeight;
    private String iconUrl;
    private String claimZoneImageUrl;
    private Boolean autoGrant;
    private String claimExpireType;
    private String claimExpireValue;
    private Integer maxRetry;
    private Boolean enabled;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String activityCode;
}
