package com.marketing.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.domain.entity.TaskFilter;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskFilterMapper extends BaseMapper<TaskFilter> {
}
