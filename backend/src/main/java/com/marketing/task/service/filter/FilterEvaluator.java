package com.marketing.task.service.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.mapper.TaskFilterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FilterEvaluator {
    private final TaskFilterMapper taskFilterMapper;
    private final FilterExpressionEngine expressionEngine;

    public boolean match(Task task, UserContext userContext) {
        return taskFilterMapper.selectList(new LambdaQueryWrapper<TaskFilter>()
                        .eq(TaskFilter::getTaskId, task.getId())
                        .eq(TaskFilter::getEnabled, true)
                        .orderByAsc(TaskFilter::getSeq))
                .stream()
                .allMatch(filter -> expressionEngine.evaluate(filter.getExpression(), userContext));
    }
}
