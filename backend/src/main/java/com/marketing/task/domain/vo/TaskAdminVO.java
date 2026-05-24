package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "任务 VO（管理端）")
public class TaskAdminVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String periodType;
    private String cronExpr;
    private String specialCycleKey;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer version;
    private Long mutexGroupId;
    private String mutexGroupName;
    private String grayType;
    private String grayConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer stepCount;
    private Integer instanceCount;

    public static TaskAdminVO from(Task task) {
        if (task == null) return null;
        TaskAdminVO vo = new TaskAdminVO();
        vo.setId(task.getId());
        vo.setCode(task.getCode());
        vo.setName(task.getName());
        vo.setDescription(task.getDescription());
        vo.setPeriodType(task.getPeriodType());
        vo.setCronExpr(task.getCronExpr());
        vo.setSpecialCycleKey(task.getSpecialCycleKey());
        vo.setStartTime(task.getStartTime());
        vo.setEndTime(task.getEndTime());
        vo.setStatus(task.getStatus());
        vo.setVersion(task.getVersion());
        vo.setMutexGroupId(task.getMutexGroupId());
        vo.setGrayType(task.getGrayType());
        vo.setGrayConfig(task.getGrayConfig());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    public Task toEntity() {
        Task task = new Task();
        task.setId(this.id);
        task.setCode(this.code);
        task.setName(this.name);
        task.setDescription(this.description);
        task.setPeriodType(this.periodType);
        task.setCronExpr(this.cronExpr);
        task.setSpecialCycleKey(this.specialCycleKey);
        task.setStartTime(this.startTime);
        task.setEndTime(this.endTime);
        task.setStatus(this.status);
        task.setVersion(this.version);
        task.setMutexGroupId(this.mutexGroupId);
        task.setGrayType(this.grayType);
        task.setGrayConfig(this.grayConfig);
        return task;
    }
}
