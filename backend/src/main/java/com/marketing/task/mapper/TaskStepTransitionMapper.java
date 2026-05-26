package com.marketing.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.domain.entity.TaskStepTransition;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskStepTransitionMapper extends BaseMapper<TaskStepTransition> {

    @Select("SELECT * FROM task_step_transition WHERE step_id = #{stepId} ORDER BY priority ASC")
    List<TaskStepTransition> selectByStepId(Long stepId);

    @Delete("DELETE FROM task_step_transition WHERE step_id IN (SELECT id FROM task_step WHERE task_id = #{taskId})")
    int deleteByTaskId(Long taskId);

    @Select("SELECT tst.* FROM task_step_transition tst INNER JOIN task_step ts ON tst.step_id = ts.id WHERE ts.task_id = #{taskId} ORDER BY tst.step_id, tst.priority ASC")
    List<TaskStepTransition> selectByTaskId(Long taskId);
}
