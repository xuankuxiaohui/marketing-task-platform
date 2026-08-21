package com.mkt.activity.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("act_activity")
public class ActActivityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String richText;
    private String contentHash;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer version;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime schedulePublishAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime scheduleOfflineAt;
    private String grayType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer grayRatio;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String submodules;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long participationPrizeId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String allowUserIds;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String allowCrowdCodes;
    private Integer newUserOnly;
    private Integer newUserDays;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer userDailyLimit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer userTotalLimit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer globalDailyLimit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String regions;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate quotaDay;
    private Integer quotaCount;
    private String draftContent;
    private Integer pendingRevision;
    private Integer deleted;
    private Long createdBy;
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

    public String getRichText() {
        return richText;
    }

    public void setRichText(String richText) {
        this.richText = richText;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getSchedulePublishAt() {
        return schedulePublishAt;
    }

    public void setSchedulePublishAt(LocalDateTime schedulePublishAt) {
        this.schedulePublishAt = schedulePublishAt;
    }

    public LocalDateTime getScheduleOfflineAt() {
        return scheduleOfflineAt;
    }

    public void setScheduleOfflineAt(LocalDateTime scheduleOfflineAt) {
        this.scheduleOfflineAt = scheduleOfflineAt;
    }

    public String getGrayType() {
        return grayType;
    }

    public void setGrayType(String grayType) {
        this.grayType = grayType;
    }

    public Integer getGrayRatio() {
        return grayRatio;
    }

    public void setGrayRatio(Integer grayRatio) {
        this.grayRatio = grayRatio;
    }

    public String getSubmodules() {
        return submodules;
    }

    public void setSubmodules(String submodules) {
        this.submodules = submodules;
    }

    public Long getParticipationPrizeId() {
        return participationPrizeId;
    }

    public void setParticipationPrizeId(Long participationPrizeId) {
        this.participationPrizeId = participationPrizeId;
    }

    public String getAllowUserIds() {
        return allowUserIds;
    }

    public void setAllowUserIds(String allowUserIds) {
        this.allowUserIds = allowUserIds;
    }

    public String getAllowCrowdCodes() {
        return allowCrowdCodes;
    }

    public void setAllowCrowdCodes(String allowCrowdCodes) {
        this.allowCrowdCodes = allowCrowdCodes;
    }

    public Integer getNewUserOnly() {
        return newUserOnly;
    }

    public void setNewUserOnly(Integer newUserOnly) {
        this.newUserOnly = newUserOnly;
    }

    public Integer getNewUserDays() {
        return newUserDays;
    }

    public void setNewUserDays(Integer newUserDays) {
        this.newUserDays = newUserDays;
    }

    public Integer getUserDailyLimit() {
        return userDailyLimit;
    }

    public void setUserDailyLimit(Integer userDailyLimit) {
        this.userDailyLimit = userDailyLimit;
    }

    public Integer getUserTotalLimit() {
        return userTotalLimit;
    }

    public void setUserTotalLimit(Integer userTotalLimit) {
        this.userTotalLimit = userTotalLimit;
    }

    public Integer getGlobalDailyLimit() {
        return globalDailyLimit;
    }

    public void setGlobalDailyLimit(Integer globalDailyLimit) {
        this.globalDailyLimit = globalDailyLimit;
    }

    public String getRegions() {
        return regions;
    }

    public void setRegions(String regions) {
        this.regions = regions;
    }

    public LocalDate getQuotaDay() {
        return quotaDay;
    }

    public void setQuotaDay(LocalDate quotaDay) {
        this.quotaDay = quotaDay;
    }

    public Integer getQuotaCount() {
        return quotaCount;
    }

    public void setQuotaCount(Integer quotaCount) {
        this.quotaCount = quotaCount;
    }

    public String getDraftContent() {
        return draftContent;
    }

    public void setDraftContent(String draftContent) {
        this.draftContent = draftContent;
    }

    public Integer getPendingRevision() {
        return pendingRevision;
    }

    public void setPendingRevision(Integer pendingRevision) {
        this.pendingRevision = pendingRevision;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
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

    public boolean deletedFlag() {
        return deleted != null && deleted != 0;
    }

    public boolean pendingFlag() {
        return pendingRevision != null && pendingRevision != 0;
    }

    public boolean newUserOnlyFlag() {
        return newUserOnly != null && newUserOnly != 0;
    }

    public int versionOrZero() {
        return version == null ? 0 : version;
    }

    public int quotaCountOrZero() {
        return quotaCount == null ? 0 : quotaCount;
    }

    public int newUserDaysOrDefault() {
        return newUserDays == null || newUserDays < 1 ? 7 : newUserDays;
    }
}
