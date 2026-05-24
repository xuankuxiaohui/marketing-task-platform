package com.marketing.task.prize.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.prize.domain.entity.PrizeRecord;
import com.marketing.task.prize.domain.enums.PrizeRecordStatus;
import com.marketing.task.prize.mapper.PrizeRecordMapper;
import com.marketing.task.prize.service.ClaimResult;
import com.marketing.task.prize.service.ClaimService;
import com.marketing.task.prize.service.PrizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/client/prize")
@RequiredArgsConstructor
public class ClientPrizeController {
    private final PrizeService prizeService;
    private final ClaimService claimService;
    private final PrizeRecordMapper recordMapper;

    @GetMapping("/records")
    public Result<Map<String, Object>> records(@RequestParam(required = false) String status) {
        String userId = UserContextHolder.get().getUserId();
        expireOverdueForUser(userId);
        List<PrizeRecord> records = prizeService.getUserRecords(userId, status);
        Map<String, Long> counts = prizeService.getStatusCounts(userId);
        return Result.ok(Map.of("records", records, "counts", counts));
    }

    @PostMapping("/{recordId}/claim")
    public Result<ClaimResult> claim(@PathVariable Long recordId) {
        String userId = UserContextHolder.get().getUserId();
        PrizeRecord record = prizeService.getRecord(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            return Result.fail(ErrorCode.NOT_FOUND, "中奖记录不存在");
        }
        if (record.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.fail(ErrorCode.BAD_REQUEST, "奖品已过期");
        }
        ClaimResult result = claimService.claim(recordId);
        if (result.getErrorMessage() != null) {
            return Result.fail(ErrorCode.INTERNAL_ERROR, result.getErrorMessage());
        }
        return Result.ok(result);
    }

    @GetMapping("/{recordId}")
    public Result<PrizeRecord> detail(@PathVariable Long recordId) {
        String userId = UserContextHolder.get().getUserId();
        PrizeRecord record = prizeService.getRecord(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            return Result.fail(ErrorCode.NOT_FOUND, "中奖记录不存在");
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
