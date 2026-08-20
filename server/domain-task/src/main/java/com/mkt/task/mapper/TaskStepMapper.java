package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskStepEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskStepMapper extends BaseMapper<TaskStepEntity> {

    List<TaskStepEntity> selectByTaskId(@Param("taskId") long taskId);

    int deleteByTaskId(@Param("taskId") long taskId);
}
