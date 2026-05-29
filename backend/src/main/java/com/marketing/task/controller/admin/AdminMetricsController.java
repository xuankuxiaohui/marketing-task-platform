package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.activity.domain.dto.ActivityDailyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.marketing.task.activity.domain.dto.ActivityOverviewVO;
import com.marketing.task.activity.domain.dto.ActivitySummaryVO;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityStats;
import com.marketing.task.activity.mapper.ActivityMapper;
import com.marketing.task.activity.mapper.ActivityStatsMapper;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskMetrics;
import com.marketing.task.mapper.TaskMetricsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Admin - Metrics", description = "数据指标")
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {
    private final TaskMetricsMapper taskMetricsMapper;
    private final ActivityStatsMapper activityStatsMapper;
    private final ActivityMapper activityMapper;

    @Operation(summary = "仪表盘概览")
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

    @Operation(summary = "任务指标汇总")
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

    @Operation(summary = "任务每日指标")
    @GetMapping("/task/{taskId}/daily")
    public Result<List<TaskMetrics>> daily(@PathVariable Long taskId,
                                            @RequestParam String from,
                                            @RequestParam String to) {
        return Result.ok(taskMetricsMapper.selectDaily(
            taskId, LocalDate.parse(from), LocalDate.parse(to)));
    }

    // --- Activity-level statistics ---

    @Operation(summary = "活动指标概览")
    @GetMapping("/activities")
    public Result<List<ActivityOverviewVO>> activityOverview() {
        List<ActivityStats> allStats = activityStatsMapper.selectList(
                new LambdaQueryWrapper<ActivityStats>());

        Map<String, long[]> aggMap = new java.util.LinkedHashMap<>();
        for (ActivityStats s : allStats) {
            long[] agg = aggMap.computeIfAbsent(s.getActivityCode(), k -> new long[3]);
            agg[0] += s.getParticipantCount();
            agg[1] += s.getCompletionCount();
            agg[2] += s.getRewardCount();
        }

        Map<String, String> nameMap = activityMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(Activity::getCode, Activity::getName, (a, b) -> a));

        List<ActivityOverviewVO> result = aggMap.entrySet().stream().map(e -> {
            long[] agg = e.getValue();
            ActivityOverviewVO vo = new ActivityOverviewVO();
            vo.setActivityCode(e.getKey());
            vo.setActivityName(nameMap.getOrDefault(e.getKey(), ""));
            vo.setParticipantCount(agg[0]);
            vo.setCompletionCount(agg[1]);
            vo.setRewardCount(agg[2]);
            return vo;
        }).toList();

        return Result.ok(result);
    }

    @Operation(summary = "活动指标汇总")
    @GetMapping("/activity/{activityCode}/summary")
    public Result<ActivitySummaryVO> activitySummary(@PathVariable String activityCode) {
        List<ActivityStats> all = activityStatsMapper.selectList(
                new LambdaQueryWrapper<ActivityStats>()
                        .eq(ActivityStats::getActivityCode, activityCode));
        long totalParticipants = all.stream().mapToLong(ActivityStats::getParticipantCount).sum();
        long totalCompletions = all.stream().mapToLong(ActivityStats::getCompletionCount).sum();
        long totalRewards = all.stream().mapToLong(ActivityStats::getRewardCount).sum();

        ActivitySummaryVO vo = new ActivitySummaryVO();
        vo.setActivityCode(activityCode);
        vo.setTotalParticipants(totalParticipants);
        vo.setTotalCompletions(totalCompletions);
        vo.setTotalRewards(totalRewards);
        return Result.ok(vo);
    }

    @Operation(summary = "活动每日指标")
    @GetMapping("/activity/{activityCode}/daily")
    public Result<List<ActivityDailyVO>> activityDaily(@PathVariable String activityCode,
                                                      @RequestParam String from,
                                                      @RequestParam String to) {
        List<ActivityStats> stats = activityStatsMapper.selectList(
                new LambdaQueryWrapper<ActivityStats>()
                        .eq(ActivityStats::getActivityCode, activityCode)
                        .ge(ActivityStats::getStatDate, LocalDate.parse(from))
                        .le(ActivityStats::getStatDate, LocalDate.parse(to))
                        .orderByAsc(ActivityStats::getStatDate));

        List<ActivityDailyVO> result = stats.stream().map(s -> {
            ActivityDailyVO vo = new ActivityDailyVO();
            vo.setActivityCode(s.getActivityCode());
            vo.setStatDate(s.getStatDate());
            vo.setParticipantCount(s.getParticipantCount());
            vo.setCompletionCount(s.getCompletionCount());
            vo.setRewardCount(s.getRewardCount());
            return vo;
        }).toList();

        return Result.ok(result);
    }
}
