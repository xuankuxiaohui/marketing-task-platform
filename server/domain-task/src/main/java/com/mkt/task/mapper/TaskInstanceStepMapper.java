package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskInstanceStepEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskInstanceStepMapper extends BaseMapper<TaskInstanceStepEntity> {

    List<TaskInstanceStepEntity> selectByInstanceId(@Param("instanceId") long instanceId);

    int activate(
            @Param("id") long id, @Param("activatedAt") LocalDateTime activatedAt);

    int complete(@Param("id") long id, @Param("completedAt") LocalDateTime completedAt);
}
