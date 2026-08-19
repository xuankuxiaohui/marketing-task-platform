package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskStepPlatformActionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskStepPlatformActionMapper extends BaseMapper<TaskStepPlatformActionEntity> {

    List<TaskStepPlatformActionEntity> selectByStepIds(@Param("stepIds") List<Long> stepIds);

    int deleteByStepIds(@Param("stepIds") List<Long> stepIds);
}
