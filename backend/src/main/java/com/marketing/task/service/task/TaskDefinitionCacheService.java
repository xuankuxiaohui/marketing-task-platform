package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.domain.entity.TaskPlatform;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.mapper.TaskFilterMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.TaskPlatformMapper;
import com.marketing.task.mapper.TaskStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskDefinitionCacheService {
    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskFilterMapper taskFilterMapper;
    private final TaskPlatformMapper taskPlatformMapper;

    @Cacheable(value = "taskDefinitions", key = "#id")
    public Task getTask(Long id) {
        return taskMapper.selectById(id);
    }

    @Cacheable(value = "taskSteps", key = "#taskId")
    public List<TaskStep> getSteps(Long taskId) {
        return taskStepMapper.selectList(new LambdaQueryWrapper<TaskStep>()
                .eq(TaskStep::getTaskId, taskId)
                .orderByAsc(TaskStep::getSeq));
    }

    @Cacheable(value = "taskFilters", key = "#taskId")
    public List<TaskFilter> getFilters(Long taskId) {
        return taskFilterMapper.selectList(new LambdaQueryWrapper<TaskFilter>()
                .eq(TaskFilter::getTaskId, taskId)
                .eq(TaskFilter::getEnabled, true)
                .orderByAsc(TaskFilter::getSeq));
    }

    @Cacheable(value = "taskPlatforms", key = "#taskId")
    public List<TaskPlatform> getPlatforms(Long taskId) {
        return taskPlatformMapper.selectList(new LambdaQueryWrapper<TaskPlatform>()
                .eq(TaskPlatform::getTaskId, taskId));
    }

    @CacheEvict(value = {"taskDefinitions", "taskSteps", "taskFilters", "taskPlatforms"}, key = "#taskId")
    public void evict(Long taskId) {
        // eviction only
    }
}
