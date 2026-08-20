package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskProgressReportEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskProgressReportMapper extends BaseMapper<TaskProgressReportEntity> {

    TaskProgressReportEntity selectByDedup(
            @Param("instanceId") long instanceId,
            @Param("stepCode") String stepCode,
            @Param("reportId") String reportId);

    int deleteBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
