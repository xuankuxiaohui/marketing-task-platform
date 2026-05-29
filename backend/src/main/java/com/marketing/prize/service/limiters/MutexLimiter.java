package com.marketing.prize.service.limiters;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.context.UserContext;
import com.marketing.prize.domain.entity.Prize;
import com.marketing.prize.domain.entity.PrizeRecord;
import com.marketing.prize.domain.enums.PrizeRecordStatus;
import com.marketing.prize.mapper.PrizeRecordMapper;
import com.marketing.prize.service.PrizeLimiter;
import com.marketing.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MutexLimiter implements PrizeLimiter {
    private final PrizeRecordMapper recordMapper;

    @Override
    public Optional<String> check(UserContext user, Prize prize, LocalDateTime now) {
        if (prize.getLimitsJson() == null || prize.getLimitsJson().isBlank()) {
            return Optional.empty();
        }
        List<Long> mutexPrizeIds = parseMutexIds(prize.getLimitsJson());
        if (mutexPrizeIds.isEmpty()) {
            return Optional.empty();
        }

        List<PrizeRecord> conflicts = recordMapper.selectList(
                new LambdaQueryWrapper<PrizeRecord>()
                        .eq(PrizeRecord::getUserId, user.getUserId())
                        .in(PrizeRecord::getPrizeId, mutexPrizeIds)
                        .in(PrizeRecord::getStatus,
                                PrizeRecordStatus.WON.name(),
                                PrizeRecordStatus.CLAIMING.name(),
                                PrizeRecordStatus.GRANTED.name()));

        if (!conflicts.isEmpty()) {
            return Optional.of("您已中过与该奖品互斥的其他奖品");
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseMutexIds(String limitsJson) {
        try {
            Map<String, Object> limits = JsonUtil.jsonToObj(limitsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (limits == null) return Collections.emptyList();
            Object mutexIds = limits.get("mutex_prize_ids");
            if (mutexIds instanceof List) {
                return ((List<Number>) mutexIds).stream().map(Number::longValue).toList();
            }
        } catch (Exception e) {
            log.warn("Failed to parse mutex prize ids from limits_json: {}", limitsJson, e);
        }
        return Collections.emptyList();
    }
}
