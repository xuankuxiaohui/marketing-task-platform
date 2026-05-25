package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.TaskDefinitionSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "任务版本快照 VO")
public class TaskVersionVO {
    private Long id;
    private Long taskId;
    private Integer version;
    private LocalDateTime createdAt;

    public static TaskVersionVO from(TaskDefinitionSnapshot entity) {
        if (entity == null) return null;
        TaskVersionVO vo = new TaskVersionVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setVersion(entity.getVersion());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
