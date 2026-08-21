package com.mkt.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.task.entity.TaskInstanceEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskInstanceMapper extends BaseMapper<TaskInstanceEntity> {

    TaskInstanceEntity selectByUserTaskCycle(
            @Param("userId") long userId, @Param("taskId") long taskId, @Param("cycleKey") String cycleKey);

    List<TaskInstanceEntity> selectByUser(@Param("userId") long userId);

    List<TaskInstanceEntity> selectByUserAndStatus(
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("categoryTaskIds") List<Long> categoryTaskIds,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countByUserAndStatus(
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("categoryTaskIds") List<Long> categoryTaskIds);

    List<TaskInstanceEntity> selectInProgressByUser(@Param("userId") long userId);

    int countToday(
            @Param("userId") long userId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    int existsInProgress(
            @Param("userId") long userId,
            @Param("taskIds") List<Long> taskIds,
            @Param("cycleKey") String cycleKey);

    long countInProgress(@Param("userId") long userId);

    long countHistory(@Param("userId") long userId);

    int completeInstance(
            @Param("id") long id,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("costSeconds") int costSeconds);

    List<TaskInstanceEntity> selectAdminPage(
            @Param("taskId") Long taskId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("simulated") Integer simulated,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countAdminPage(
            @Param("taskId") Long taskId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("simulated") Integer simulated,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    List<TaskInstanceEntity> selectDueToExpire(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int abandonCas(
            @Param("id") long id,
            @Param("source") String source,
            @Param("abandonedAt") LocalDateTime abandonedAt,
            @Param("costSeconds") int costSeconds);

    int expireCas(
            @Param("id") long id, @Param("now") LocalDateTime now, @Param("costSeconds") int costSeconds);
}
