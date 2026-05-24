package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.UserTaskInstance;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "用户任务实例 VO")
public class UserTaskInstanceVO {
    private Long id;
    private String userId;
    private Long taskId;
    private Integer taskVersion;
    private String cycleKey;
    private String status;
    private Integer currentStepSeq;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private LocalDateTime rewardTime;
    private LocalDateTime createdAt;
    @Schema(description = "任务名称（管理端注入，非实体字段）")
    private String taskName;

    public static UserTaskInstanceVO from(UserTaskInstance entity) {
        if (entity == null) return null;
        UserTaskInstanceVO vo = new UserTaskInstanceVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setTaskId(entity.getTaskId());
        vo.setTaskVersion(entity.getTaskVersion());
        vo.setCycleKey(entity.getCycleKey());
        vo.setStatus(entity.getStatus());
        vo.setCurrentStepSeq(entity.getCurrentStepSeq());
        vo.setStartTime(entity.getStartTime());
        vo.setCompleteTime(entity.getCompleteTime());
        vo.setRewardTime(entity.getRewardTime());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
