package com.marketing.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@TableName("activity_stats")
public class ActivityStats {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String activityCode;
    private LocalDate statDate;
    private Integer participantCount;
    private Integer completionCount;
    private Integer rewardCount;
}
