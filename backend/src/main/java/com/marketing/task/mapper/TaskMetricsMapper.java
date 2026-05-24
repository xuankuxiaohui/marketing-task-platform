package com.marketing.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.domain.entity.TaskMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TaskMetricsMapper extends BaseMapper<TaskMetrics> {

    @Select("SELECT * FROM task_metrics WHERE task_id = #{taskId} AND metric_date BETWEEN #{from} AND #{to} ORDER BY metric_date DESC")
    List<TaskMetrics> selectDaily(@Param("taskId") Long taskId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT * FROM task_metrics WHERE metric_date = #{date} ORDER BY views DESC LIMIT #{limit}")
    List<TaskMetrics> selectTopByDate(@Param("date") LocalDate date, @Param("limit") int limit);
}
