package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("list_data")
public class ListData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String listType;
    private String listKey;
    private String userId;
    private LocalDateTime createdAt;
}
