package com.marketing.task.signin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("point_account")
public class PointAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long balance;
    private Long totalEarned;
    private Long totalSpent;
    private Long totalExpired;
    private LocalDateTime updatedAt;
}
