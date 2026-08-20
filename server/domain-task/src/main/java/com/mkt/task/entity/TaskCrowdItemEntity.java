package com.mkt.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("task_crowd_item")
public class TaskCrowdItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long crowdId;
    private Long userId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCrowdId() {
        return crowdId;
    }

    public void setCrowdId(Long crowdId) {
        this.crowdId = crowdId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
