package com.mkt.task.mapper;

import com.mkt.task.entity.TaskVersionSnapshotEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Insert/select only (RL-12 / R12 snapshot immutability). */
@Mapper
public interface TaskVersionSnapshotMapper {

    int insert(TaskVersionSnapshotEntity entity);

    TaskVersionSnapshotEntity selectById(@Param("id") long id);

    TaskVersionSnapshotEntity selectByTaskAndVersion(@Param("taskId") long taskId, @Param("version") int version);

    List<TaskVersionSnapshotEntity> selectByTaskId(@Param("taskId") long taskId);
}
