package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskMetrics;
import com.marketing.task.mapper.TaskMetricsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {
    private final TaskMetricsMapper taskMetricsMapper;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        LocalDate today = LocalDate.now();
        List<TaskMetrics> topTasks = taskMetricsMapper.selectTopByDate(today, 10);
        long totalViews = topTasks.stream().mapToLong(TaskMetrics::getViews).sum();
        long totalParticipants = topTasks.stream().mapToLong(TaskMetrics::getParticipants).sum();
        long totalCompletions = topTasks.stream().mapToLong(TaskMetrics::getCompletions).sum();
        long totalRewardSuccess = topTasks.stream().mapToLong(TaskMetrics::getRewardSuccess).sum();
        long totalRewardFailure = topTasks.stream().mapToLong(TaskMetrics::getRewardFailure).sum();

        TaskMetrics todayAgg = new TaskMetrics();
        todayAgg.setMetricDate(today);
        todayAgg.setViews(totalViews);
        todayAgg.setParticipants(totalParticipants);
        todayAgg.setCompletions(totalCompletions);
        todayAgg.setRewardSuccess(totalRewardSuccess);
        todayAgg.setRewardFailure(totalRewardFailure);

        return Result.ok(Map.of(
            "today", todayAgg,
            "topTasks", topTasks
        ));
    }

    @GetMapping("/task/{taskId}/summary")
    public Result<Map<String, Object>> summary(@PathVariable Long taskId) {
        List<TaskMetrics> all = taskMetricsMapper.selectList(
            new LambdaQueryWrapper<TaskMetrics>()
                .eq(TaskMetrics::getTaskId, taskId)
        );
        long totalViews = all.stream().mapToLong(TaskMetrics::getViews).sum();
        long totalParticipants = all.stream().mapToLong(TaskMetrics::getParticipants).sum();
        long totalCompletions = all.stream().mapToLong(TaskMetrics::getCompletions).sum();
        long totalRewardSuccess = all.stream().mapToLong(TaskMetrics::getRewardSuccess).sum();
        long totalRewardFailure = all.stream().mapToLong(TaskMetrics::getRewardFailure).sum();

        return Result.ok(Map.of(
            "taskId", taskId,
            "totalViews", totalViews,
            "totalParticipants", totalParticipants,
            "totalCompletions", totalCompletions,
            "totalRewardSuccess", totalRewardSuccess,
            "totalRewardFailure", totalRewardFailure
        ));
    }

    @GetMapping("/task/{taskId}/daily")
    public Result<List<TaskMetrics>> daily(@PathVariable Long taskId,
                                            @RequestParam String from,
                                            @RequestParam String to) {
        return Result.ok(taskMetricsMapper.selectDaily(
            taskId, LocalDate.parse(from), LocalDate.parse(to)));
    }
}
