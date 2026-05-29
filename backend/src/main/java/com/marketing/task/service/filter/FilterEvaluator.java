package com.marketing.task.service.filter;

import com.marketing.context.UserContext;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
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
                    .allMatch(filter -> {
                        try {
                            return expressionEngine.evaluate(filter.getExpression(), userContext);
                        } catch (Exception ex) {
                            log.warn("Filter match error for task {}: {}", task.getId(), ex.getMessage());
                            return false;
                        }
                    });
        } finally {
            expressionEngine.clearTaskGrayConfig();
        }
    }
}
