package com.mkt.reward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rwd_prize")
public class PrizeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String imageUrl;
    private String description;
    private String categoryCode;
    private String typeParams;
    private String rewardTarget;
    private String fulfillmentMode;
    private Integer unitCostFen;
    private Integer totalStock;
    private Integer remainingStock;
    private Integer dailyClaimLimit;
    private Integer totalClaimLimit;
    private String regionLimit;
    private String levelLimit;
    private String tagLimit;
    private String claimMode;
    private String reconActionPolicy;
    private Integer expireHours;
    private Long groupId;
    private String status;
    private String extConfig;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getTypeParams() {
        return typeParams;
    }

    public void setTypeParams(String typeParams) {
        this.typeParams = typeParams;
    }

    public String getRewardTarget() {
        return rewardTarget;
    }

    public void setRewardTarget(String rewardTarget) {
        this.rewardTarget = rewardTarget;
    }

    public String getFulfillmentMode() {
        return fulfillmentMode;
    }

    public void setFulfillmentMode(String fulfillmentMode) {
        this.fulfillmentMode = fulfillmentMode;
    }

    public Integer getUnitCostFen() {
        return unitCostFen;
    }

    public void setUnitCostFen(Integer unitCostFen) {
        this.unitCostFen = unitCostFen;
    }

    public Integer getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(Integer totalStock) {
        this.totalStock = totalStock;
    }

    public Integer getRemainingStock() {
        return remainingStock;
    }

    public void setRemainingStock(Integer remainingStock) {
        this.remainingStock = remainingStock;
    }

    public Integer getDailyClaimLimit() {
        return dailyClaimLimit;
    }

    public void setDailyClaimLimit(Integer dailyClaimLimit) {
        this.dailyClaimLimit = dailyClaimLimit;
    }

    public Integer getTotalClaimLimit() {
        return totalClaimLimit;
    }

    public void setTotalClaimLimit(Integer totalClaimLimit) {
        this.totalClaimLimit = totalClaimLimit;
    }

    public String getRegionLimit() {
        return regionLimit;
    }

    public void setRegionLimit(String regionLimit) {
        this.regionLimit = regionLimit;
    }

    public String getLevelLimit() {
        return levelLimit;
    }

    public void setLevelLimit(String levelLimit) {
        this.levelLimit = levelLimit;
    }

    public String getTagLimit() {
        return tagLimit;
    }

    public void setTagLimit(String tagLimit) {
        this.tagLimit = tagLimit;
    }

    public String getClaimMode() {
        return claimMode;
    }

    public void setClaimMode(String claimMode) {
        this.claimMode = claimMode;
    }

    public String getReconActionPolicy() {
        return reconActionPolicy;
    }

    public void setReconActionPolicy(String reconActionPolicy) {
        this.reconActionPolicy = reconActionPolicy;
    }

    public Integer getExpireHours() {
        return expireHours;
    }

    public void setExpireHours(Integer expireHours) {
        this.expireHours = expireHours;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExtConfig() {
        return extConfig;
    }

    public void setExtConfig(String extConfig) {
        this.extConfig = extConfig;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public boolean deletedFlag() {
        return deleted != null && deleted == 1;
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
