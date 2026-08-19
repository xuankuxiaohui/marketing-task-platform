package com.mkt.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("task_mutex_group")
public class TaskMutexGroupEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private Integer crossCycle;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCrossCycle() {
        return crossCycle;
    }

    public void setCrossCycle(Integer crossCycle) {
        this.crossCycle = crossCycle;
    }

    public boolean crossCycleFlag() {
        return crossCycle != null && crossCycle == 1;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
