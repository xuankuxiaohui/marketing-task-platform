package com.marketing.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("participation_log")
public class ParticipationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String activityCode;
    private Long userId;
    private String clientIp;
    private String checkerResult;
    private String failCode;
    private String failMessage;
    private LocalDateTime createdAt;
}
