package com.marketing.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_log")
public class EventLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private Long taskId;
    private Long instanceId;
    private Long stepId;
    private String userId;
    private String platform;
    private String eventData;
    private LocalDateTime createdAt;
}
