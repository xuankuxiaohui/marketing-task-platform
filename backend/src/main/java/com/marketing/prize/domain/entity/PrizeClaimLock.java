package com.marketing.prize.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prize_claim_lock")
public class PrizeClaimLock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private LocalDateTime createdAt;
}
