package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reward_record")
public class RewardRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long stepId;
    private String rewardType;
    private String rewardConfigJson;
    private String status;
    private String idempotentKey;
    private String errorMessage;
    private LocalDateTime createdAt;
}
