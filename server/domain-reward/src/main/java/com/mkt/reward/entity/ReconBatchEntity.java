package com.mkt.reward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("rwd_recon_batch")
public class ReconBatchEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String categoryCode;
    private LocalDate billDate;
    private String status;
    private Integer platformCount;
    private Integer channelCount;
    private Integer matchedCount;
    private Integer platformOnly;
    private Integer channelOnly;
    private Integer amountMismatch;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(LocalDate billDate) {
        this.billDate = billDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPlatformCount() {
        return platformCount;
    }

    public void setPlatformCount(Integer platformCount) {
        this.platformCount = platformCount;
    }

    public Integer getChannelCount() {
        return channelCount;
    }

    public void setChannelCount(Integer channelCount) {
        this.channelCount = channelCount;
    }

    public Integer getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(Integer matchedCount) {
        this.matchedCount = matchedCount;
    }

    public Integer getPlatformOnly() {
        return platformOnly;
    }

    public void setPlatformOnly(Integer platformOnly) {
        this.platformOnly = platformOnly;
    }

    public Integer getChannelOnly() {
        return channelOnly;
    }

    public void setChannelOnly(Integer channelOnly) {
        this.channelOnly = channelOnly;
    }

    public Integer getAmountMismatch() {
        return amountMismatch;
    }

    public void setAmountMismatch(Integer amountMismatch) {
        this.amountMismatch = amountMismatch;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
