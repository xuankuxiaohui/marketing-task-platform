package com.mkt.reward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rwd_recon_item")
public class ReconItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long grantRecordId;
    private String fulfillmentRef;
    private Integer platformCostFen;
    private Integer channelAmountFen;
    private String result;
    private String action;
    private String actionRef;
    private String reviewStatus;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private String reviewRemark;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getGrantRecordId() {
        return grantRecordId;
    }

    public void setGrantRecordId(Long grantRecordId) {
        this.grantRecordId = grantRecordId;
    }

    public String getFulfillmentRef() {
        return fulfillmentRef;
    }

    public void setFulfillmentRef(String fulfillmentRef) {
        this.fulfillmentRef = fulfillmentRef;
    }

    public Integer getPlatformCostFen() {
        return platformCostFen;
    }

    public void setPlatformCostFen(Integer platformCostFen) {
        this.platformCostFen = platformCostFen;
    }

    public Integer getChannelAmountFen() {
        return channelAmountFen;
    }

    public void setChannelAmountFen(Integer channelAmountFen) {
        this.channelAmountFen = channelAmountFen;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActionRef() {
        return actionRef;
    }

    public void setActionRef(String actionRef) {
        this.actionRef = actionRef;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewRemark() {
        return reviewRemark;
    }

    public void setReviewRemark(String reviewRemark) {
        this.reviewRemark = reviewRemark;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
