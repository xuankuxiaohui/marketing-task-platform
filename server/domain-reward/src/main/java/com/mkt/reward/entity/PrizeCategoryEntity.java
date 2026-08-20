package com.mkt.reward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rwd_prize_category")
public class PrizeCategoryEntity {

    @TableId(type = IdType.INPUT)
    private String code;
    private String name;
    private String rewardTarget;
    private String fulfillmentMode;
    private String costMode;
    private Integer reconRequired;
    private String reconActionPolicy;
    private String adapterCode;
    private String paramSchema;
    private Integer builtin;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public String getCostMode() {
        return costMode;
    }

    public void setCostMode(String costMode) {
        this.costMode = costMode;
    }

    public Integer getReconRequired() {
        return reconRequired;
    }

    public void setReconRequired(Integer reconRequired) {
        this.reconRequired = reconRequired;
    }

    public boolean reconRequiredFlag() {
        return reconRequired != null && reconRequired == 1;
    }

    public String getReconActionPolicy() {
        return reconActionPolicy;
    }

    public void setReconActionPolicy(String reconActionPolicy) {
        this.reconActionPolicy = reconActionPolicy;
    }

    public String getAdapterCode() {
        return adapterCode;
    }

    public void setAdapterCode(String adapterCode) {
        this.adapterCode = adapterCode;
    }

    public String getParamSchema() {
        return paramSchema;
    }

    public void setParamSchema(String paramSchema) {
        this.paramSchema = paramSchema;
    }

    public Integer getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Integer builtin) {
        this.builtin = builtin;
    }

    public boolean builtinFlag() {
        return builtin != null && builtin == 1;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
