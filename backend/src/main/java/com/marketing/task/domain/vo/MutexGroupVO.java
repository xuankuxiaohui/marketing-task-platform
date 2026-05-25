package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.MutexGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "互斥组 VO")
public class MutexGroupVO {
    private Long id;
    private String name;
    private String description;
    private String scope;
    private Boolean crossCycle;
    private Integer taskCount;
    private LocalDateTime createdAt;

    public static MutexGroupVO from(MutexGroup entity) {
        if (entity == null) return null;
        MutexGroupVO vo = new MutexGroupVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setScope(entity.getScope());
        vo.setCrossCycle(entity.getCrossCycle());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
