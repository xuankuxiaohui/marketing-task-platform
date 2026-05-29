package com.marketing.prize.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prize_record")
public class PrizeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long instanceId;
    private Long taskId;
    private Long stepId;
    private Long prizeId;
    private Integer quantity;
    private String idempotentKey;
    private String prizeType;
    private String prizeName;
    private String prizeIcon;
    private String prizeImage;
    private String prizeParamsJson;
    private String status;
    private LocalDateTime expireTime;
    private Integer retryCount;
    private String errorMessage;
    private String externalTradeNo;
    private LocalDateTime wonAt;
    private LocalDateTime claimedAt;
    private LocalDateTime grantedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
    private String activityCode;
}
