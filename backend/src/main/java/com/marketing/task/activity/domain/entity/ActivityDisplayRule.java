package com.marketing.task.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("activity_display_rule")
public class ActivityDisplayRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String activityCode;
    private String content;
    private String contentHash;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
