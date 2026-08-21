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

    TaskInstanceStepEntity selectByInstanceAndCode(
            @Param("instanceId") long instanceId, @Param("stepCode") String stepCode);

    int activate(
            @Param("id") long id, @Param("activatedAt") LocalDateTime activatedAt);

    int complete(@Param("id") long id, @Param("completedAt") LocalDateTime completedAt);

    int completeCas(
            @Param("id") long id,
            @Param("version") int version,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("progressCurrent") Integer progressCurrent);

    int skipCas(
            @Param("id") long id,
            @Param("version") int version,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("skipReason") String skipReason);

    int addProgressCas(
            @Param("id") long id,
            @Param("version") int version,
            @Param("progressCurrent") int progressCurrent);

    int updateLastBizNo(@Param("id") long id, @Param("lastBizNo") String lastBizNo);
}
