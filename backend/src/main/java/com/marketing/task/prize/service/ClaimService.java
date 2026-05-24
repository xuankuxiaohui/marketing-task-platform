package com.marketing.task.prize.service;

import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.domain.entity.PrizeClaimLock;
import com.marketing.task.prize.domain.entity.PrizeRecord;
import com.marketing.task.prize.domain.enums.PrizeRecordStatus;
import com.marketing.task.prize.mapper.PrizeClaimLockMapper;
import com.marketing.task.prize.mapper.PrizeMapper;
import com.marketing.task.prize.mapper.PrizeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {
    private final PrizeRecordMapper recordMapper;
    private final PrizeMapper prizeMapper;
    private final PrizeClaimLockMapper lockMapper;
    private final ApplicationContext applicationContext;

    @Transactional
    public ClaimResult claim(Long recordId) {
        PrizeRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.PRIZE_RECORD_NOT_FOUND);
        }

        String currentStatus = record.getStatus();
        if (PrizeRecordStatus.GRANTED.name().equals(currentStatus)) {
            return ClaimResult.granted(record.getExternalTradeNo());
        }
        if (PrizeRecordStatus.EXPIRED.name().equals(currentStatus)) {
            return ClaimResult.failed("奖品已过期");
        }
        if (PrizeRecordStatus.FAILED_PERMANENTLY.name().equals(currentStatus)) {
            return ClaimResult.failed("奖品发放永久失败，请联系客服");
        }
        if (!PrizeRecordStatus.WON.name().equals(currentStatus)
                && !PrizeRecordStatus.FAILED.name().equals(currentStatus)) {
            return ClaimResult.failed("当前状态不允许领奖: " + currentStatus);
        }

        if (record.getExpireTime().isBefore(LocalDateTime.now())) {
            expireRecord(record);
            return ClaimResult.failed("奖品已过期");
        }

        try {
            PrizeClaimLock lock = new PrizeClaimLock();
            lock.setRecordId(recordId);
            lock.setCreatedAt(LocalDateTime.now());
            lockMapper.insert(lock);
        } catch (DuplicateKeyException e) {
            return ClaimResult.inProgress();
        }

        try {
            record.setStatus(PrizeRecordStatus.CLAIMING.name());
            record.setClaimedAt(LocalDateTime.now());
            recordMapper.updateById(record);

            Prize prize = prizeMapper.selectById(record.getPrizeId());
            if (prize == null) {
                throw new BusinessException(ErrorCode.PRIZE_CONFIG_MISSING);
            }

            PrizeHandler handler = applicationContext.getBean(prize.getHandlerBean(), PrizeHandler.class);
            handler.validate(prize);
            GrantResult result = handler.grant(record, prize);

            if (result.isSuccess()) {
                record.setStatus(PrizeRecordStatus.GRANTED.name());
                record.setExternalTradeNo(result.getTradeNo());
                record.setGrantedAt(LocalDateTime.now());
                record.setErrorMessage(null);
                recordMapper.updateById(record);
                log.info("领奖成功: recordId={}, tradeNo={}", recordId, result.getTradeNo());
                return ClaimResult.granted(result.getTradeNo());
            } else {
                record.setStatus(PrizeRecordStatus.FAILED.name());
                record.setErrorMessage(result.getErrorMessage());
                record.setRetryCount(record.getRetryCount() + 1);
                int maxRetry = prize.getMaxRetry() != null ? prize.getMaxRetry() : 3;
                if (record.getRetryCount() >= maxRetry) {
                    record.setStatus(PrizeRecordStatus.FAILED_PERMANENTLY.name());
                }
                recordMapper.updateById(record);
                log.warn("领奖失败: recordId={}, error={}", recordId, result.getErrorMessage());
                return ClaimResult.failed(result.getErrorMessage());
            }
        } catch (Exception e) {
            record.setStatus(PrizeRecordStatus.FAILED.name());
            record.setErrorMessage(e.getMessage());
            record.setRetryCount(record.getRetryCount() + 1);
            Prize prize = prizeMapper.selectById(record.getPrizeId());
            int maxRetry = prize != null && prize.getMaxRetry() != null ? prize.getMaxRetry() : 3;
            if (record.getRetryCount() >= maxRetry) {
                record.setStatus(PrizeRecordStatus.FAILED_PERMANENTLY.name());
            }
            recordMapper.updateById(record);
            log.error("领奖异常: recordId={}", recordId, e);
            return ClaimResult.failed(e.getMessage());
        } finally {
            lockMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PrizeClaimLock>()
                    .eq(PrizeClaimLock::getRecordId, recordId));
        }
    }

    private void expireRecord(PrizeRecord record) {
        record.setStatus(PrizeRecordStatus.EXPIRED.name());
        recordMapper.updateById(record);
    }
}
