package com.marketing.task.signin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.signin.domain.entity.PointTransaction;
import com.marketing.task.signin.domain.enums.PointTransactionStatus;
import com.marketing.task.signin.mapper.PointTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointExpiryScheduler {
    private final PointTransactionMapper transactionMapper;
    private final PointService pointService;

    @Scheduled(cron = "0 15 * * * ?")
    public void expirePoints() {
        LocalDateTime now = LocalDateTime.now();
        List<PointTransaction> expired = transactionMapper.selectList(
                new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getStatus, PointTransactionStatus.ACTIVE.name())
                        .le(PointTransaction::getExpireAt, now)
                        .last("LIMIT 200"));

        for (PointTransaction tx : expired) {
            try {
                pointService.expire(tx.getId());
            } catch (Exception e) {
                log.error("积分过期处理失败: txId={}, error={}", tx.getId(), e.getMessage(), e);
            }
        }

        if (!expired.isEmpty()) {
            log.info("积分过期调度: 处理 {} 条", expired.size());
        }
    }
}
