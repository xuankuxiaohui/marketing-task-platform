package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.OperationLog;
import com.marketing.task.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/admin/operation-logs")
@RequiredArgsConstructor
public class AdminOperationLogController {
    private final OperationLogMapper operationLogMapper;

    @GetMapping
    public Result<IPage<OperationLog>> list(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String operationType,
                                             @RequestParam(required = false) String targetType,
                                             @RequestParam(required = false) String operatorId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(OperationLog::getOperationType, operationType);
        }
        if (targetType != null && !targetType.isBlank()) {
            wrapper.eq(OperationLog::getTargetType, targetType);
        }
        if (operatorId != null && !operatorId.isBlank()) {
            wrapper.eq(OperationLog::getOperatorId, operatorId);
        }
        if (startDate != null) {
            wrapper.ge(OperationLog::getCreatedAt, LocalDateTime.of(startDate, LocalTime.MIN));
        }
        if (endDate != null) {
            wrapper.le(OperationLog::getCreatedAt, LocalDateTime.of(endDate, LocalTime.MAX));
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        IPage<OperationLog> result = operationLogMapper.selectPage(Page.of(page, size), wrapper);
        return Result.ok(result);
    }
}
