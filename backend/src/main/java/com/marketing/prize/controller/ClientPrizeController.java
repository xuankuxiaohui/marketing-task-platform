package com.marketing.prize.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.marketing.context.UserContextHolder;
import com.marketing.prize.domain.entity.PrizeRecord;
import com.marketing.prize.domain.enums.PrizeRecordStatus;
import com.marketing.prize.mapper.PrizeRecordMapper;
import com.marketing.prize.service.ClaimResult;
import com.marketing.prize.service.ClaimService;
import com.marketing.prize.service.PrizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Client - Prizes", description = "C端奖品")
@RestController
@RequestMapping("/api/client/prize")
@RequiredArgsConstructor
public class ClientPrizeController {
    private final PrizeService prizeService;
    private final ClaimService claimService;
    private final PrizeRecordMapper recordMapper;

    @Operation(summary = "获取奖品记录列表")
    @GetMapping("/records")
    public Result<Map<String, Object>> records(@RequestParam(required = false) String status) {
        String userId = UserContextHolder.get().getUserId();
        expireOverdueForUser(userId);
        List<PrizeRecord> records = prizeService.getUserRecords(userId, status);
        Map<String, Long> counts = prizeService.getStatusCounts(userId);
        return Result.ok(Map.of("records", records, "counts", counts));
    }

    @Operation(summary = "领取奖品")
    @PostMapping("/{recordId}/claim")
    public Result<ClaimResult> claim(@PathVariable Long recordId) {
        String userId = UserContextHolder.get().getUserId();
        PrizeRecord record = prizeService.getRecord(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PRIZE_RECORD_NOT_FOUND);
        }
        if (record.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PRIZE_CLAIM_EXPIRED);
        }
        ClaimResult result = claimService.claim(recordId);
        if (result.getErrorMessage() != null) {
            throw new BusinessException(ErrorCode.PRIZE_HANDLER_ERROR, result.getErrorMessage());
        }
        return Result.ok(result);
    }

    @Operation(summary = "获取奖品记录详情")
    @GetMapping("/{recordId}")
    public Result<PrizeRecord> detail(@PathVariable Long recordId) {
        String userId = UserContextHolder.get().getUserId();
        PrizeRecord record = prizeService.getRecord(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PRIZE_RECORD_NOT_FOUND);
        }
        return Result.ok(record);
    }

    private void expireOverdueForUser(String userId) {
        recordMapper.update(null, new LambdaUpdateWrapper<PrizeRecord>()
                .eq(PrizeRecord::getUserId, userId)
                .eq(PrizeRecord::getStatus, PrizeRecordStatus.WON.name())
                .lt(PrizeRecord::getExpireTime, LocalDateTime.now())
                .set(PrizeRecord::getStatus, PrizeRecordStatus.EXPIRED.name()));
    }
}
