package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.cycle.CycleKeyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCycleScheduler {
    private final TaskMapper taskMapper;
    private final UserTaskInstanceMapper instanceMapper;
    private final CycleKeyResolver cycleKeyResolver;
    private final Map<Long, String> lastCycleKey = new ConcurrentHashMap<>();

    @Scheduled(cron = "0 */5 * * * ?")
    public void scanAndActivateCycles() {
        LocalDateTime now = LocalDateTime.now();
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getStatus, "PUBLISHED")
                        .in(Task::getPeriodType, "CRON", "MONTHLY")
                        .and(w -> w.isNull(Task::getStartTime).or().le(Task::getStartTime, now))
                        .and(w -> w.isNull(Task::getEndTime).or().ge(Task::getEndTime, now)));

        for (Task task : tasks) {
            String cycleKey = cycleKeyResolver.resolve(task);
            String previous = lastCycleKey.put(task.getId(), cycleKey);

            if (!cycleKey.equals(previous)) {
                Long existingCount = instanceMapper.selectCount(
                        new LambdaQueryWrapper<UserTaskInstance>()
                                .eq(UserTaskInstance::getTaskId, task.getId())
                                .eq(UserTaskInstance::getCycleKey, cycleKey));

                log.info("[TaskCycleScheduler] 新周期激活: taskId={}, name={}, periodType={}, cycleKey={}, existingInstances={}",
                        task.getId(), task.getName(), task.getPeriodType(), cycleKey, existingCount);
            }
        }
    }
}
