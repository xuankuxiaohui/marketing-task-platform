package com.marketing.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.domain.entity.TaskStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskStepMapper extends BaseMapper<TaskStep> {

    @Select("SELECT MAX(seq) FROM task_step WHERE task_id = #{taskId}")
    Integer selectMaxSeq(@Param("taskId") Long taskId);
}
