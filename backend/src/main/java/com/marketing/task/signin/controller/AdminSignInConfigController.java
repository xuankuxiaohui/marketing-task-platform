package com.marketing.task.signin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.service.OperationLogService;
import com.marketing.task.signin.domain.entity.PointTransaction;
import com.marketing.task.signin.domain.entity.SignInConfig;
import com.marketing.task.signin.domain.entity.SignInRecord;
import com.marketing.task.signin.domain.enums.SignInConfigStatus;
import com.marketing.task.signin.mapper.SignInConfigMapper;
import com.marketing.task.signin.mapper.SignInRecordMapper;
import com.marketing.task.signin.service.PointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "Admin - Sign-in", description = "签到管理")
@RestController
@RequestMapping("/api/admin/signin")
@RequiredArgsConstructor
public class AdminSignInConfigController {
    private final SignInConfigMapper configMapper;
    private final SignInRecordMapper recordMapper;
    private final PointService pointService;
    private final OperationLogService operationLogService;

    @Operation(summary = "分页查询签到配置列表")
    @GetMapping("/configs")
    public Result<Page<SignInConfig>> listConfigs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        Page<SignInConfig> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SignInConfig> wrapper = new LambdaQueryWrapper<SignInConfig>()
                .orderByDesc(SignInConfig::getCreatedAt);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SignInConfig::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SignInConfig::getName, keyword);
        }
        return Result.ok(configMapper.selectPage(pageParam, wrapper));
    }

    @Operation(summary = "查询签到配置详情")
    @GetMapping("/configs/{id}")
    public Result<SignInConfig> getConfig(@PathVariable Long id) {
        return Result.ok(requireConfig(id));
    }

    @Operation(summary = "创建签到配置")
    @PostMapping("/configs")
    public Result<SignInConfig> createConfig(@RequestBody SignInConfig config) {
        config.setId(null);
        config.setStatus(SignInConfigStatus.DRAFT.name());
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        config.setCreatedBy(UserContextHolder.get().getUserId());
        configMapper.insert(config);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "CREATE", "SIGNIN_CONFIG", config.getId(), config.getName(), null);
        return Result.ok(config);
    }

    @Operation(summary = "更新签到配置")
    @PutMapping("/configs/{id}")
    public Result<SignInConfig> updateConfig(@PathVariable Long id, @RequestBody SignInConfig config) {
        SignInConfig existing = requireConfig(id);
        if (SignInConfigStatus.PUBLISHED.name().equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布的活动不可修改");
        }
        config.setId(id);
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(UserContextHolder.get().getUserId());
        configMapper.updateById(config);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "UPDATE", "SIGNIN_CONFIG", id, config.getName(), null);
        return Result.ok(configMapper.selectById(id));
    }

    @Operation(summary = "发布签到活动")
    @PostMapping("/configs/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        SignInConfig config = requireConfig(id);
        if (!SignInConfigStatus.DRAFT.name().equals(config.getStatus())
                && !SignInConfigStatus.OFFLINE.name().equals(config.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可发布");
        }
        config.setStatus(SignInConfigStatus.PUBLISHED.name());
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "PUBLISH", "SIGNIN_CONFIG", id, config.getName(), null);
        return Result.ok(null);
    }

    @Operation(summary = "下线签到活动")
    @PostMapping("/configs/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        SignInConfig config = requireConfig(id);
        if (!SignInConfigStatus.PUBLISHED.name().equals(config.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可下线");
        }
        config.setStatus(SignInConfigStatus.OFFLINE.name());
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "OFFLINE", "SIGNIN_CONFIG", id, config.getName(), null);
        return Result.ok(null);
    }

    @Operation(summary = "删除签到配置")
    @DeleteMapping("/configs/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SignInConfig config = requireConfig(id);
        if (SignInConfigStatus.PUBLISHED.name().equals(config.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布的活动不可删除");
        }
        configMapper.deleteById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "DELETE", "SIGNIN_CONFIG", id, config.getName(), null);
        return Result.ok(null);
    }

    @Operation(summary = "查询签到统计")
    @GetMapping("/configs/{id}/stats")
    public Result<Map<String, Object>> getStats(@PathVariable Long id) {
        requireConfig(id);
        long todayCount = recordMapper.selectCount(
                new LambdaQueryWrapper<SignInRecord>()
                        .eq(SignInRecord::getConfigId, id)
                        .eq(SignInRecord::getSigninDate, java.time.LocalDate.now()));
        long totalCount = recordMapper.selectCount(
                new LambdaQueryWrapper<SignInRecord>()
                        .eq(SignInRecord::getConfigId, id));
        return Result.ok(Map.of(
                "todaySigned", todayCount,
                "totalSigned", totalCount));
    }

    @Operation(summary = "查询签到记录")
    @GetMapping("/configs/{id}/records")
    public Result<Page<SignInRecord>> getRecords(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId) {
        requireConfig(id);
        Page<SignInRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SignInRecord> wrapper = new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getConfigId, id)
                .orderByDesc(SignInRecord::getSigninDate);
        if (userId != null && !userId.isBlank()) {
            wrapper.eq(SignInRecord::getUserId, userId);
        }
        return Result.ok(recordMapper.selectPage(pageParam, wrapper));
    }

    @Operation(summary = "查询积分交易记录")
    @GetMapping("/points/transactions")
    public Result<Page<PointTransaction>> getPointTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String type) {
        return Result.ok(pointService.getTransactions(userId, type, page, size));
    }

    @Operation(summary = "手动发放积分")
    @PostMapping("/points/grant")
    public Result<Void> grantPoints(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("userId");
        long amount = ((Number) body.get("amount")).longValue();
        String description = (String) body.getOrDefault("description", "管理员手动发放");
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId 不能为空");
        }
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "积分数量必须大于0");
        }
        pointService.earn(userId, amount, "ADMIN_GRANT", null, null, description);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "GRANT_POINTS", "POINT", null,
                "给用户" + userId + "发放" + amount + "积分", null);
        return Result.ok(null);
    }

    private SignInConfig requireConfig(Long id) {
        SignInConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "签到活动不存在");
        }
        return config;
    }
}
