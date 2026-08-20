package com.mkt.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("task_definition")
public class TaskDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String category;
    private String iconUrl;
    private String badgeText;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer sortWeight;
    private String status;
    private Integer version;
    private LocalDateTime schedulePublishAt;
    private String cycleType;
    private String cronExpr;
    private LocalDateTime specialStart;
    private LocalDateTime specialEnd;
    private Long mutexGroupId;
    private String grayType;
    private Integer grayRatio;
    private String grayAbGroup;
    private Long grayCrowdId;
    private Long grayExcludeCrowdId;
    private String filterExpr;
    private String filterAllowCrowdIds;
    private String filterExcludeCrowdIds;
    private Integer pendingRevision;
    private LocalDateTime offlineAt;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public void setBadgeText(String badgeText) {
        this.badgeText = badgeText;
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

    public Integer getSortWeight() {
        return sortWeight;
    }

    public void setSortWeight(Integer sortWeight) {
        this.sortWeight = sortWeight;
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

    public String getCycleType() {
        return cycleType;
    }

    public void setCycleType(String cycleType) {
        this.cycleType = cycleType;
    }

    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    public LocalDateTime getSpecialStart() {
        return specialStart;
    }

    public void setSpecialStart(LocalDateTime specialStart) {
        this.specialStart = specialStart;
    }

    public LocalDateTime getSpecialEnd() {
        return specialEnd;
    }

    public void setSpecialEnd(LocalDateTime specialEnd) {
        this.specialEnd = specialEnd;
    }

    public Long getMutexGroupId() {
        return mutexGroupId;
    }

    public void setMutexGroupId(Long mutexGroupId) {
        this.mutexGroupId = mutexGroupId;
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

    public String getGrayAbGroup() {
        return grayAbGroup;
    }

    public void setGrayAbGroup(String grayAbGroup) {
        this.grayAbGroup = grayAbGroup;
    }

    public Long getGrayCrowdId() {
        return grayCrowdId;
    }

    public void setGrayCrowdId(Long grayCrowdId) {
        this.grayCrowdId = grayCrowdId;
    }

    public Long getGrayExcludeCrowdId() {
        return grayExcludeCrowdId;
    }

    public void setGrayExcludeCrowdId(Long grayExcludeCrowdId) {
        this.grayExcludeCrowdId = grayExcludeCrowdId;
    }

    public String getFilterExpr() {
        return filterExpr;
    }

    public void setFilterExpr(String filterExpr) {
        this.filterExpr = filterExpr;
    }

    public String getFilterAllowCrowdIds() {
        return filterAllowCrowdIds;
    }

    public void setFilterAllowCrowdIds(String filterAllowCrowdIds) {
        this.filterAllowCrowdIds = filterAllowCrowdIds;
    }

    public String getFilterExcludeCrowdIds() {
        return filterExcludeCrowdIds;
    }

    public void setFilterExcludeCrowdIds(String filterExcludeCrowdIds) {
        this.filterExcludeCrowdIds = filterExcludeCrowdIds;
    }

    public Integer getPendingRevision() {
        return pendingRevision;
    }

    public void setPendingRevision(Integer pendingRevision) {
        this.pendingRevision = pendingRevision;
    }

    public LocalDateTime getOfflineAt() {
        return offlineAt;
    }

    public void setOfflineAt(LocalDateTime offlineAt) {
        this.offlineAt = offlineAt;
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
}
