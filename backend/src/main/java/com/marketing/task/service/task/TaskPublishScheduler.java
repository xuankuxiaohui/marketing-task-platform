package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.enums.TaskStatus;
import com.marketing.task.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPublishScheduler {
    private final TaskMapper taskMapper;
    private final TaskService taskService;

    @Scheduled(cron = "0 * * * * ?")
    public void scanAndPublishScheduledTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getStatus, TaskStatus.SCHEDULED.name())
                        .le(Task::getScheduledPublishAt, now));

        for (Task task : tasks) {
            try {
                log.info("[TaskPublishScheduler] 定时发布任务: taskId={}, name={}, scheduledAt={}",
                        task.getId(), task.getName(), task.getScheduledPublishAt());
                taskService.publish(task.getId());
                log.info("[TaskPublishScheduler] 定时发布成功: taskId={}", task.getId());
            } catch (Exception e) {
                log.error("[TaskPublishScheduler] 定时发布失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
            }
        }
    }
}
