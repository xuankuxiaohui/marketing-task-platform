package com.marketing.task.prize.service.limiters;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.context.UserContext;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.domain.entity.PrizeInventoryRecord;
import com.marketing.task.prize.mapper.PrizeInventoryRecordMapper;
import com.marketing.task.prize.service.PrizeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InventoryLimiter implements PrizeLimiter {
    private final PrizeInventoryRecordMapper inventoryMapper;

    @Override
    public Optional<String> check(UserContext user, Prize prize, LocalDateTime now) {
        List<PrizeInventoryRecord> records = inventoryMapper.selectList(
                new LambdaQueryWrapper<PrizeInventoryRecord>()
                        .eq(PrizeInventoryRecord::getPrizeId, prize.getId()));
        int totalUsed = records.stream().mapToInt(PrizeInventoryRecord::getQuantity).sum();

        if (prize.getTotalStock() != null && totalUsed >= prize.getTotalStock()) {
            return Optional.of("奖品总库存不足");
        }

        LocalDateTime monthStart = YearMonth.from(now).atDay(1).atStartOfDay();
        LocalDateTime dayStart = LocalDate.from(now).atStartOfDay();
        int monthUsed = records.stream()
                .filter(r -> !r.getCreatedAt().isBefore(monthStart))
                .mapToInt(PrizeInventoryRecord::getQuantity).sum();
        int dayUsed = records.stream()
                .filter(r -> !r.getCreatedAt().isBefore(dayStart))
                .mapToInt(PrizeInventoryRecord::getQuantity).sum();

        if (prize.getMonthlyStock() != null && monthUsed >= prize.getMonthlyStock()) {
            return Optional.of("奖品本月库存不足");
        }
        if (prize.getDailyStock() != null && dayUsed >= prize.getDailyStock()) {
            return Optional.of("奖品本日库存不足");
        }
        return Optional.empty();
    }
}
