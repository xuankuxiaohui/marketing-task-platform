package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.context.SimulateContextHolder;
import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.*;
import com.marketing.task.domain.enums.Platform;
import com.marketing.task.domain.vo.UserTaskInstanceVO;
import com.marketing.task.mapper.EventLogMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.mapper.UserTaskStepProgressMapper;
import com.marketing.task.service.step.StepAdvanceEngine;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import com.marketing.task.service.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/simulate")
@RequiredArgsConstructor
public class AdminSimulateController {
    private final TaskService taskService;
    private final StepAdvanceEngine stepAdvanceEngine;
    private final UserTaskInstanceMapper instanceMapper;
    private final UserTaskStepProgressMapper progressMapper;
    private final EventLogMapper eventLogMapper;
    private final TaskDefinitionCacheService cacheService;

    @PostMapping("/impersonate")
    public Result<Map<String, Object>> impersonate(@RequestBody Map<String, Object> body) {
        String userId = getString(body, "userId", "sim_user");
        String province = getString(body, "province", "SH");
        String role = getString(body, "role", "user");
        int level = getInt(body, "level", 1);
        @SuppressWarnings("unchecked")
        Set<String> tags = new HashSet<>((List<String>) body.getOrDefault("tags", List.of()));
        String platform = getString(body, "platform", "IOS");
        String orgId = getString(body, "orgId", null);

        UserContext ctx = UserContext.builder()
            .userId(userId)
            .province(province)
            .role(role)
            .level(level)
            .tags(tags)
            .platform(Platform.valueOf(platform))
            .orgId(orgId)
            .build();
        SimulateContextHolder.set(ctx);
        return Result.ok(Map.of("userId", ctx.getUserId(), "platform", ctx.getPlatform().name()));
    }

    @DeleteMapping("/impersonate")
    public Result<Void> clearImpersonate() {
        SimulateContextHolder.clear();
        return Result.ok(null);
    }

    @PostMapping("/callback")
    public Result<UserTaskInstanceVO> simulateCallback(@RequestBody Map<String, Object> body) {
        Long instanceId = toLong(body.get("instanceId"));
        String eventKey = (String) body.get("eventKey");
        UserTaskInstance instance = taskService.requireInstance(instanceId);
        return Result.ok(UserTaskInstanceVO.from(stepAdvanceEngine.callback(instance, eventKey)));
    }

    @PostMapping("/progress")
    public Result<UserTaskInstanceVO> simulateProgress(@RequestBody Map<String, Object> body) {
        Long instanceId = toLong(body.get("instanceId"));
        Long stepId = toLong(body.get("stepId"));
        Double progressValue = toDouble(body.get("progressValue"));
        UserTaskInstance instance = taskService.requireInstance(instanceId);
        return Result.ok(UserTaskInstanceVO.from(
            stepAdvanceEngine.progress(instance, stepId, progressValue.intValue())));
    }

    @PostMapping("/full-flow/{taskId}")
    public Result<List<Map<String, Object>>> fullFlow(@PathVariable Long taskId) {
        UserContext ctx = SimulateContextHolder.get();
        if (ctx == null) {
            ctx = UserContext.builder()
                .userId("sim_user_auto")
                .platform(Platform.IOS)
                .build();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        Task task = taskService.requireTask(taskId);
        UserTaskInstance instance = taskService.getOrCreateInstance(task, ctx);

        results.add(Map.of("step", "INSTANCE_CREATED", "status", "success",
            "instanceId", instance.getId(), "finalStatus", instance.getStatus(),
            "currentStepSeq", instance.getCurrentStepSeq()));

        return Result.ok(results);
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        UserContext ctx = SimulateContextHolder.get();
        return Result.ok(Map.of("simulating", ctx != null,
            "user", ctx != null ? Map.of("userId", ctx.getUserId(), "platform", ctx.getPlatform().name()) : "none"));
    }

    // ---- new standalone simulation APIs ----

    @GetMapping("/test-users")
    public Result<List<Map<String, Object>>> testUsers() {
        List<UserTaskInstance> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<UserTaskInstance>()
                        .select(UserTaskInstance::getUserId, UserTaskInstance::getCreatedAt)
                        .orderByDesc(UserTaskInstance::getCreatedAt)
                        .last("LIMIT 200"));

        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> users = new ArrayList<>();
        for (UserTaskInstance i : instances) {
            if (seen.add(i.getUserId())) {
                Map<String, Object> m = new HashMap<>();
                m.put("userId", i.getUserId());
                users.add(m);
                if (users.size() >= 50) break;
            }
        }

        return Result.ok(users);
    }

    @PostMapping("/flow")
    public Result<Map<String, Object>> startFlow(@RequestBody Map<String, Object> body) {
        String userId = getString(body, "userId", null);
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }

        Long taskId = toLong(body.get("taskId"));
        String province = getString(body, "province", "SH");
        String platform = getString(body, "platform", "IOS");

        UserContext ctx = UserContext.builder()
                .userId(userId)
                .province(province)
                .platform(Platform.valueOf(platform))
                .role(getString(body, "role", "user"))
                .level(getInt(body, "level", 1))
                .tags(new HashSet<>())
                .build();
        SimulateContextHolder.set(ctx);

        Task task = taskService.requireTask(taskId);
        UserTaskInstance instance = taskService.getOrCreateInstance(task, ctx);

        return Result.ok(buildInstanceDetail(instance));
    }

    @GetMapping("/instance/{instanceId}/detail")
    public Result<Map<String, Object>> instanceDetail(@PathVariable Long instanceId) {
        UserTaskInstance instance = taskService.requireInstance(instanceId);
        return Result.ok(buildInstanceDetail(instance));
    }

    @PostMapping("/click")
    public Result<Map<String, Object>> simulateClick(@RequestBody Map<String, Object> body) {
        Long instanceId = toLong(body.get("instanceId"));
        Long stepId = toLong(body.get("stepId"));
        UserTaskInstance instance = taskService.requireInstance(instanceId);
        UserTaskInstance updated = stepAdvanceEngine.click(instance, stepId);
        return Result.ok(buildInstanceDetail(updated));
    }

    @GetMapping("/instance/{instanceId}/events")
    public Result<List<EventLog>> instanceEvents(@PathVariable Long instanceId) {
        List<EventLog> events = eventLogMapper.selectList(
                new LambdaQueryWrapper<EventLog>()
                        .eq(EventLog::getInstanceId, instanceId)
                        .orderByDesc(EventLog::getCreatedAt)
                        .last("LIMIT 100"));
        return Result.ok(events);
    }

    private Map<String, Object> buildInstanceDetail(UserTaskInstance instance) {
        List<TaskStep> steps = cacheService.getSteps(instance.getTaskId());

        List<UserTaskStepProgress> progressList = progressMapper.selectList(
                new LambdaQueryWrapper<UserTaskStepProgress>()
                        .eq(UserTaskStepProgress::getInstanceId, instance.getId()));

        Map<Long, UserTaskStepProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(UserTaskStepProgress::getStepId, p -> p, (a, b) -> a));

        List<Map<String, Object>> stepItems = steps.stream().map(step -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepId", step.getId());
            item.put("seq", step.getSeq());
            item.put("code", step.getCode());
            item.put("name", step.getName());
            item.put("description", step.getDescription());
            item.put("type", step.getType());
            item.put("targetValue", step.getTargetValue());
            item.put("callbackEventKey", step.getCallbackEventKey());
            item.put("flowDesc", step.getFlowDesc());

            boolean isCurrent = step.getSeq().equals(instance.getCurrentStepSeq());
            item.put("isCurrentStep", isCurrent);

            UserTaskStepProgress progress = progressMap.get(step.getId());
            if (progress != null) {
                item.put("progressStatus", progress.getStatus());
                item.put("progressValue", progress.getProgressValue());
            } else {
                item.put("progressStatus", null);
                item.put("progressValue", null);
            }
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", UserTaskInstanceVO.from(instance));
        result.put("steps", stepItems);
        return result;
    }

    // ---- helper methods ----

    private String getString(Map<String, Object> body, String key, String defaultVal) {
        Object val = body.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private int getInt(Map<String, Object> body, String key, int defaultVal) {
        Object val = body.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) return Integer.parseInt(s);
        return defaultVal;
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) return Long.parseLong(s);
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot convert to Long: " + val);
    }

    private Double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) return Double.parseDouble(s);
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot convert to Double: " + val);
    }
}
