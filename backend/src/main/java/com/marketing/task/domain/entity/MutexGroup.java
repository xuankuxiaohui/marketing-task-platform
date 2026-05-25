package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mutex_group")
public class MutexGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String scope;
    private Boolean crossCycle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
