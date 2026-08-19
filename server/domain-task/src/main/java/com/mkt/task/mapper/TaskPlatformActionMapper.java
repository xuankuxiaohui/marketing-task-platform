package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskPlatformActionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskPlatformActionMapper extends BaseMapper<TaskPlatformActionEntity> {

    List<TaskPlatformActionEntity> selectByTaskId(@Param("taskId") long taskId);

    int deleteByTaskId(@Param("taskId") long taskId);
}
