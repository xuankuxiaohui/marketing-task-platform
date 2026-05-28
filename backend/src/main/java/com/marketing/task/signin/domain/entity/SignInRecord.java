package com.marketing.task.signin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("signin_record")
public class SignInRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long configId;
    private String userId;
    private LocalDate signinDate;
    private String periodKey;
    private Integer streakDay;
    private Integer basePoints;
    private Integer bonusPoints;
    private Integer totalPoints;
    private Integer tierReached;
    @TableField("is_catch_up")
    private Boolean catchUp;
    private LocalDateTime createdAt;
}
