package com.marketing.task.prize.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.marketing.task.prize.domain.entity.PrizeRecord;
import com.marketing.task.prize.domain.enums.PrizeRecordStatus;
import com.marketing.task.prize.mapper.PrizeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrizeExpiryScheduler {
    private final PrizeRecordMapper recordMapper;

    @Scheduled(cron = "0 7 * * * *")
    @Transactional
    public void expireOverdueRecords() {
        List<PrizeRecord> overdue = recordMapper.selectList(
                new LambdaQueryWrapper<PrizeRecord>()
                        .eq(PrizeRecord::getStatus, PrizeRecordStatus.WON.name())
                        .lt(PrizeRecord::getExpireTime, LocalDateTime.now())
                        .last("LIMIT 200"));

        if (overdue.isEmpty()) {
            return;
        }

        for (PrizeRecord record : overdue) {
            recordMapper.update(null, new LambdaUpdateWrapper<PrizeRecord>()
                    .eq(PrizeRecord::getId, record.getId())
                    .set(PrizeRecord::getStatus, PrizeRecordStatus.EXPIRED.name()));
        }
        log.info("定时过期处理完成: {} 条记录", overdue.size());
    }
}
