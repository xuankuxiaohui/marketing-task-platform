package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskDefinitionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskDefinitionMapper extends BaseMapper<TaskDefinitionEntity> {

    TaskDefinitionEntity selectByCode(@Param("code") String code);

    long selectCountByQuery(
            @Param("code") String code,
            @Param("name") String name,
            @Param("status") String status,
            @Param("category") String category);

    List<TaskDefinitionEntity> selectByQuery(
            @Param("code") String code,
            @Param("name") String name,
            @Param("status") String status,
            @Param("category") String category,
            @Param("offset") long offset,
            @Param("limit") int limit);

    List<String> selectCycleTypesByMutexGroup(
            @Param("mutexGroupId") long mutexGroupId, @Param("excludeTaskId") Long excludeTaskId);

    int countByMutexGroup(@Param("mutexGroupId") long mutexGroupId);

    int countReferencingCrowd(@Param("crowdId") long crowdId);

    TaskDefinitionEntity selectByIdForUpdate(@Param("id") long id);

    List<TaskDefinitionEntity> selectDueScheduled(
            @Param("now") java.time.LocalDateTime now, @Param("limit") int limit);

    int countInProgressInstances(@Param("taskId") long taskId);

    int casPublish(
            @Param("id") long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") int expectedVersion,
            @Param("expectedPending") int expectedPending,
            @Param("nextVersion") int nextVersion,
            @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
