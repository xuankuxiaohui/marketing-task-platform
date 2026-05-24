package com.marketing.task.service.filter;

import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FilterEvaluator {
    private final TaskDefinitionCacheService cacheService;
    private final FilterExpressionEngine expressionEngine;

    public boolean match(Task task, UserContext userContext) {
        try {
            expressionEngine.setTaskGrayConfig(task.getId(), task.getGrayType(), task.getGrayConfig());
            return cacheService.getFilters(task.getId())
                    .stream()
                    .allMatch(filter -> expressionEngine.evaluate(filter.getExpression(), userContext));
        } finally {
            expressionEngine.clearTaskGrayConfig();
        }
    }
}
