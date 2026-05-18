package com.marketing.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.domain.entity.TaskStep;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskStepMapper extends BaseMapper<TaskStep> {
}
