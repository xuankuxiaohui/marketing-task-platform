package com.marketing.prize.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.context.UserContext;
import com.marketing.prize.domain.entity.Prize;
import com.marketing.prize.domain.entity.PrizeInventoryRecord;
import com.marketing.prize.domain.entity.PrizeRecord;
import com.marketing.prize.domain.enums.ClaimExpireType;
import com.marketing.prize.domain.enums.PrizeRecordStatus;
import com.marketing.prize.mapper.PrizeInventoryRecordMapper;
import com.marketing.prize.mapper.PrizeMapper;
import com.marketing.prize.mapper.PrizeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrizeService {
    private final PrizeMapper prizeMapper;
    private final PrizeRecordMapper recordMapper;
    private final PrizeInventoryRecordMapper inventoryMapper;
    private final List<PrizeLimiter> limiters;
    private final ClaimService claimService;

    @Transactional
    public PrizeRecord grant(UserContext user, Long prizeId, int quantity, GrantContext ctx) {
        String idempotentKey = ctx.getIdempotentKey(prizeId);

        PrizeRecord existing = recordMapper.selectOne(
                new LambdaQueryWrapper<PrizeRecord>()
                        .eq(PrizeRecord::getIdempotentKey, idempotentKey));
        if (existing != null) {
            if (PrizeRecordStatus.GRANTED.name().equals(existing.getStatus())) {
                log.info("奖励已到账，跳过: idempotentKey={}", idempotentKey);
                return existing;
            }
            if (PrizeRecordStatus.WON.name().equals(existing.getStatus())
                    || PrizeRecordStatus.CLAIMING.name().equals(existing.getStatus())) {
                log.info("奖励已发放待领取，跳过: idempotentKey={}", idempotentKey);
                return existing;
            }
        }

        Prize prize = prizeMapper.selectById(prizeId);
        if (prize == null) {
            throw new BusinessException(ErrorCode.PRIZE_NOT_FOUND, "奖品不存在: " + prizeId);
        }

        LocalDateTime now = LocalDateTime.now();
        for (PrizeLimiter limiter : limiters) {
            Optional<String> rejection = limiter.check(user, prize, now);
            if (rejection.isPresent()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, rejection.get());
            }
        }

        PrizeInventoryRecord invRecord = new PrizeInventoryRecord();
        invRecord.setPrizeId(prizeId);
        invRecord.setQuantity(quantity);
        invRecord.setCreatedAt(now);

        PrizeRecord record = buildRecord(user, prize, quantity, ctx, idempotentKey, now);
        record.setStatus(PrizeRecordStatus.WON.name());

        recordMapper.insert(record);

        invRecord.setRecordId(record.getId());
        inventoryMapper.insert(invRecord);

        if (prize.getAutoGrant() != null && prize.getAutoGrant()) {
            claimService.claim(record.getId());
            record = recordMapper.selectById(record.getId());
        }

        return record;
    }

    private PrizeRecord buildRecord(UserContext user, Prize prize, int quantity,
                                     GrantContext ctx, String idempotentKey, LocalDateTime now) {
        PrizeRecord record = new PrizeRecord();
        record.setUserId(user.getUserId());
        record.setInstanceId(ctx.getInstanceId());
        record.setTaskId(ctx.getTaskId());
        record.setStepId(ctx.getStepId());
        record.setPrizeId(prize.getId());
        record.setQuantity(quantity);
        record.setIdempotentKey(idempotentKey);
        record.setPrizeType(prize.getType());
        record.setPrizeName(prize.getName());
        record.setPrizeIcon(prize.getIconUrl());
        record.setPrizeImage(prize.getClaimZoneImageUrl());
        record.setPrizeParamsJson(prize.getParamsJson());
        record.setActivityCode(prize.getActivityCode());
        record.setExpireTime(calcExpireTime(prize, now));
        record.setWonAt(now);
        record.setRetryCount(0);
        return record;
    }

    private LocalDateTime calcExpireTime(Prize prize, LocalDateTime now) {
        ClaimExpireType type;
        try {
            type = ClaimExpireType.valueOf(prize.getClaimExpireType());
        } catch (Exception e) {
            type = ClaimExpireType.DAYS;
        }
        return switch (type) {
            case DAYS -> now.plusDays(Long.parseLong(prize.getClaimExpireValue()));
            case CALENDAR_MONTH -> {
                int months = Integer.parseInt(prize.getClaimExpireValue());
                YearMonth ym = YearMonth.from(now).plusMonths(months);
                yield ym.atEndOfMonth().atTime(23, 59, 59);
            }
            case FIXED_DATE -> LocalDate.parse(prize.getClaimExpireValue()).atTime(23, 59, 59);
        };
    }

    public List<PrizeRecord> getUserRecords(String userId, String status) {
        LambdaQueryWrapper<PrizeRecord> qw = new LambdaQueryWrapper<PrizeRecord>()
                .eq(PrizeRecord::getUserId, userId)
                .orderByDesc(PrizeRecord::getWonAt);
        if (status != null && !status.isBlank()) {
            qw.eq(PrizeRecord::getStatus, status);
        }
        return recordMapper.selectList(qw);
    }

    public PrizeRecord getRecord(Long recordId) {
        return recordMapper.selectById(recordId);
    }

    public Map<String, Long> getStatusCounts(String userId) {
        List<PrizeRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<PrizeRecord>()
                        .eq(PrizeRecord::getUserId, userId));
        return records.stream()
                .collect(Collectors.groupingBy(PrizeRecord::getStatus, Collectors.counting()));
    }
}
