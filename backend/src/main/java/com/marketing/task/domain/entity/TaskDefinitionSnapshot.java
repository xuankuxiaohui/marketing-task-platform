package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("task_definition_snapshot")
public class TaskDefinitionSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer version;
    private String snapshotJson;
    private LocalDateTime createdAt;
}
