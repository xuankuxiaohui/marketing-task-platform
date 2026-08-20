package com.mkt.signin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sgn_activity")
public class SgnActivityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer version;
    private LocalDateTime schedulePublishAt;
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

    public int versionOrZero() {
        return version == null ? 0 : version;
    }
}
