package com.marketing.prize.service.limiters;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.context.UserContext;
import com.marketing.prize.domain.entity.Prize;
import com.marketing.prize.domain.entity.PrizeRecord;
import com.marketing.prize.domain.enums.PrizeRecordStatus;
import com.marketing.prize.mapper.PrizeRecordMapper;
import com.marketing.prize.service.PrizeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRateLimiter implements PrizeLimiter {
    private final PrizeRecordMapper recordMapper;

    @Override
    public Optional<String> check(UserContext user, Prize prize, LocalDateTime now) {
        if (prize.getUserTotalLimit() == null && prize.getUserMonthlyLimit() == null
                && prize.getUserDailyLimit() == null) {
            return Optional.empty();
        }

        List<PrizeRecord> userRecords = recordMapper.selectList(
                new LambdaQueryWrapper<PrizeRecord>()
                        .eq(PrizeRecord::getUserId, user.getUserId())
                        .eq(PrizeRecord::getPrizeId, prize.getId())
                        .in(PrizeRecord::getStatus,
                                PrizeRecordStatus.WON.name(),
                                PrizeRecordStatus.CLAIMING.name(),
                                PrizeRecordStatus.GRANTED.name()));

        int totalCount = userRecords.size();

        if (prize.getUserTotalLimit() != null && totalCount >= prize.getUserTotalLimit()) {
            return Optional.of("您已达到该奖品总领取上限");
        }

        LocalDateTime monthStart = YearMonth.from(now).atDay(1).atStartOfDay();
        LocalDateTime dayStart = LocalDate.from(now).atStartOfDay();
        long monthCount = userRecords.stream()
                .filter(r -> !r.getWonAt().isBefore(monthStart)).count();
        long dayCount = userRecords.stream()
                .filter(r -> !r.getWonAt().isBefore(dayStart)).count();

        if (prize.getUserMonthlyLimit() != null && monthCount >= prize.getUserMonthlyLimit()) {
            return Optional.of("您本月已领取该奖品达到上限");
        }
        if (prize.getUserDailyLimit() != null && dayCount >= prize.getUserDailyLimit()) {
            return Optional.of("您今日已领取该奖品达到上限");
        }
        return Optional.empty();
    }
}
