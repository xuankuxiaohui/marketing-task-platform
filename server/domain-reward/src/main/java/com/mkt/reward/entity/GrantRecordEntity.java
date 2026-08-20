package com.mkt.reward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rwd_grant_record")
public class GrantRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long prizeId;
    private String prizeCode;
    private String categoryCode;
    private Integer faceFen;
    private Integer costFen;
    private String reconStatus;
    private Long userId;
    private String grantSource;
    private String sourceId;
    private String status;
    private String fulfillmentStatus;
    private String fulfillmentRef;
    private String failReason;
    private String fulfillFailReason;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime nextFulfillRetryAt;
    private LocalDateTime expireAt;
    private LocalDateTime claimedAt;
    private LocalDateTime grantedAt;
    private LocalDateTime fulfilledAt;
    private Integer simulated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPrizeId() {
        return prizeId;
    }

    public void setPrizeId(Long prizeId) {
        this.prizeId = prizeId;
    }

    public String getPrizeCode() {
        return prizeCode;
    }

    public void setPrizeCode(String prizeCode) {
        this.prizeCode = prizeCode;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public Integer getFaceFen() {
        return faceFen;
    }

    public void setFaceFen(Integer faceFen) {
        this.faceFen = faceFen;
    }

    public Integer getCostFen() {
        return costFen;
    }

    public void setCostFen(Integer costFen) {
        this.costFen = costFen;
    }

    public String getReconStatus() {
        return reconStatus;
    }

    public void setReconStatus(String reconStatus) {
        this.reconStatus = reconStatus;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getGrantSource() {
        return grantSource;
    }

    public void setGrantSource(String grantSource) {
        this.grantSource = grantSource;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFulfillmentStatus() {
        return fulfillmentStatus;
    }

    public void setFulfillmentStatus(String fulfillmentStatus) {
        this.fulfillmentStatus = fulfillmentStatus;
    }

    public String getFulfillmentRef() {
        return fulfillmentRef;
    }

    public void setFulfillmentRef(String fulfillmentRef) {
        this.fulfillmentRef = fulfillmentRef;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getFulfillFailReason() {
        return fulfillFailReason;
    }

    public void setFulfillFailReason(String fulfillFailReason) {
        this.fulfillFailReason = fulfillFailReason;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public LocalDateTime getNextFulfillRetryAt() {
        return nextFulfillRetryAt;
    }

    public void setNextFulfillRetryAt(LocalDateTime nextFulfillRetryAt) {
        this.nextFulfillRetryAt = nextFulfillRetryAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    public LocalDateTime getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(LocalDateTime fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }

    public Integer getSimulated() {
        return simulated;
    }

    public void setSimulated(Integer simulated) {
        this.simulated = simulated;
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
