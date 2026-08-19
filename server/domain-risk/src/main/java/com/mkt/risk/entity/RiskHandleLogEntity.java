package com.mkt.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("risk_handle_log")
public class RiskHandleLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hitLogId;
    private Long userId;
    private String action;
    private Integer toWhitelist;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHitLogId() {
        return hitLogId;
    }

    public void setHitLogId(Long hitLogId) {
        this.hitLogId = hitLogId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getToWhitelist() {
        return toWhitelist;
    }

    public void setToWhitelist(Integer toWhitelist) {
        this.toWhitelist = toWhitelist;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
