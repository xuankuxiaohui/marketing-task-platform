package com.marketing.task.controller.admin;

import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.context.SimulateContextHolder;
import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.Platform;
import com.marketing.task.domain.vo.UserTaskInstanceVO;
import com.marketing.task.service.step.StepAdvanceEngine;
import com.marketing.task.service.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/simulate")
@RequiredArgsConstructor
public class AdminSimulateController {
    private final TaskService taskService;
    private final StepAdvanceEngine stepAdvanceEngine;

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
