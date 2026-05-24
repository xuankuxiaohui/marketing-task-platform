package com.marketing.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.domain.entity.TaskMetrics;
import com.marketing.task.mapper.EventLogMapper;
import com.marketing.task.mapper.TaskMetricsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMetricsScheduler {
    private final EventLogMapper eventLogMapper;
    private final TaskMetricsMapper taskMetricsMapper;

    @Scheduled(cron = "31 */5 * * * ?")
    public void aggregateMetrics() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = eventLogMapper.selectMaps(null);

        Map<Long, TaskMetrics> metricsByTask = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long taskId = (Long) row.get("task_id");
            if (taskId == null) continue;
            TaskMetrics m = metricsByTask.computeIfAbsent(taskId, k -> {
                TaskMetrics tm = new TaskMetrics();
                tm.setTaskId(k);
                tm.setMetricDate(today);
                tm.setViews(0L);
                tm.setParticipants(0L);
                tm.setCompletions(0L);
                tm.setRewardSuccess(0L);
                tm.setRewardFailure(0L);
                return tm;
            });

            String eventType = (String) row.get("event_type");
            switch (eventType) {
                case "TASK_VIEWED"       -> m.setViews(m.getViews() + 1);
                case "INSTANCE_CREATED"  -> m.setParticipants(m.getParticipants() + 1);
                case "STEP_COMPLETED"    -> m.setCompletions(m.getCompletions() + 1);
                case "REWARD_SUCCESS"    -> m.setRewardSuccess(m.getRewardSuccess() + 1);
                case "REWARD_FAILURE"    -> m.setRewardFailure(m.getRewardFailure() + 1);
            }
        }

        for (TaskMetrics m : metricsByTask.values()) {
            TaskMetrics existing = taskMetricsMapper.selectOne(
                new LambdaQueryWrapper<TaskMetrics>()
                    .eq(TaskMetrics::getTaskId, m.getTaskId())
                    .eq(TaskMetrics::getMetricDate, today)
            );
            if (existing != null) {
                m.setId(existing.getId());
                taskMetricsMapper.updateById(m);
            } else {
                taskMetricsMapper.insert(m);
            }
        }
        log.info("[TaskMetricsScheduler] Aggregated {} task metrics rows for {}", metricsByTask.size(), today);
    }
}
