package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "任务 VO（C 端）")
public class TaskClientVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String periodType;

    public static TaskClientVO from(Task task) {
        if (task == null) return null;
        TaskClientVO vo = new TaskClientVO();
        vo.setId(task.getId());
        vo.setCode(task.getCode());
        vo.setName(task.getName());
        vo.setDescription(task.getDescription());
        vo.setPeriodType(task.getPeriodType());
        return vo;
    }
}
