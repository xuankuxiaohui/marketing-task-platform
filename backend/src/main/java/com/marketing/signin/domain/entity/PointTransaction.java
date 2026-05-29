package com.marketing.signin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("point_transaction")
public class PointTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String type;
    private Long amount;
    private String sourceType;
    private Long sourceId;
    private Long balanceAfter;
    private LocalDateTime expireAt;
    private String status;
    private String description;
    private LocalDateTime createdAt;
}
