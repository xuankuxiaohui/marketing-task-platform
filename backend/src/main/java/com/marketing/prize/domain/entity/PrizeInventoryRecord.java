package com.marketing.prize.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prize_inventory_record")
public class PrizeInventoryRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long prizeId;
    private Long recordId;
    private Integer quantity;
    private LocalDateTime createdAt;
}
