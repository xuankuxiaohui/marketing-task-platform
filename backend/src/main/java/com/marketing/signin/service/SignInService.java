package com.marketing.signin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.signin.domain.dto.SignInCalendarVO;
import com.marketing.signin.domain.dto.SignInResult;
import com.marketing.signin.domain.dto.SignInStatusVO;
import com.marketing.signin.domain.dto.StreakTierConfig;
import com.marketing.signin.domain.entity.PointAccount;
import com.marketing.signin.domain.entity.SignInConfig;
import com.marketing.signin.domain.entity.SignInRecord;
import com.marketing.signin.domain.enums.SignInConfigStatus;
import com.marketing.signin.mapper.SignInConfigMapper;
import com.marketing.signin.mapper.SignInRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignInService {
    private final SignInConfigMapper configMapper;
    private final SignInRecordMapper recordMapper;
    private final PointService pointService;
    private final ObjectMapper objectMapper;

    private static final String SOURCE_SIGNIN = "SIGNIN";
    private static final String SOURCE_CATCH_UP = "CATCH_UP";

    @Transactional
    public SignInResult signIn(Long configId, String userId) {
        SignInConfig config = requireConfig(configId);
        validateConfig(config);

        LocalDate today = LocalDate.now();
        String periodKey = resolvePeriodKey(config.getPeriodType(), today);

        // Idempotent check
        SignInRecord existing = findRecord(configId, userId, today);
        if (existing != null) {
            PointAccount account = pointService.getBalance(userId);
            return SignInResult.ok(existing.getId(), existing.getStreakDay(),
                    existing.getBasePoints(), existing.getBonusPoints(),
                    existing.getTotalPoints(), existing.getTierReached(),
                    account.getBalance(), existing.getCatchUp());
        }

        // Calculate streak
        int streakDay = calculateStreak(configId, userId, today, config.getPeriodType(), periodKey);

        // Apply maxStreak cap
        StreakTierConfig tierConfig = parseStreakConfig(config.getStreakConfig());
        if (tierConfig != null && tierConfig.getMaxStreak() != null) {
            streakDay = Math.min(streakDay, tierConfig.getMaxStreak());
        }

        // Calculate points
        int basePoints = config.getBasePoints();
        int bonusPoints = 0;
        Integer tierReached = null;
        if (tierConfig != null && tierConfig.getTiers() != null) {
            for (StreakTierConfig.Tier tier : tierConfig.getTiers()) {
                if (streakDay >= tier.getDay()) {
                    bonusPoints = tier.getBonus();
                    tierReached = tier.getDay();
                }
            }
        }
        int totalPoints = basePoints + bonusPoints;

        // Create record
        SignInRecord record = new SignInRecord();
        record.setConfigId(configId);
        record.setUserId(userId);
        record.setSigninDate(today);
        record.setPeriodKey(periodKey);
        record.setStreakDay(streakDay);
        record.setBasePoints(basePoints);
        record.setBonusPoints(bonusPoints);
        record.setTotalPoints(totalPoints);
        record.setTierReached(tierReached);
        record.setCatchUp(false);
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // Concurrent sign-in, return existing
            SignInRecord dup = findRecord(configId, userId, today);
            PointAccount account = pointService.getBalance(userId);
            return SignInResult.ok(dup.getId(), dup.getStreakDay(),
                    dup.getBasePoints(), dup.getBonusPoints(),
                    dup.getTotalPoints(), dup.getTierReached(),
                    account.getBalance(), dup.getCatchUp());
        }

        // Grant points
        LocalDateTime expireAt = calculateExpireAt(config.getPointExpireDays());
        pointService.earn(userId, totalPoints, SOURCE_SIGNIN, record.getId(), expireAt,
                "每日签到: " + config.getName());

        PointAccount account = pointService.getBalance(userId);
        log.info("签到成功: userId={}, configId={}, streak={}, points={}", userId, configId, streakDay, totalPoints);
        return SignInResult.ok(record.getId(), streakDay, basePoints, bonusPoints,
                totalPoints, tierReached, account.getBalance(), false);
    }

    @Transactional
    public SignInResult catchUp(Long configId, String userId, LocalDate targetDate) {
        SignInConfig config = requireConfig(configId);
        validateConfig(config);

        if (!Boolean.TRUE.equals(config.getCatchUpEnabled())) {
            return SignInResult.fail("该活动未开启补签功能");
        }

        LocalDate today = LocalDate.now();
        if (!targetDate.isBefore(today)) {
            return SignInResult.fail("只能补签过去的日期");
        }

        if (config.getCatchUpMaxDays() != null) {
            LocalDate earliest = today.minusDays(config.getCatchUpMaxDays());
            if (targetDate.isBefore(earliest)) {
                return SignInResult.fail("只能补签最近" + config.getCatchUpMaxDays() + "天");
            }
        }

        // Check not already signed
        if (findRecord(configId, userId, targetDate) != null) {
            return SignInResult.fail("该日期已签到");
        }

        // Deduct catch-up cost
        int cost = config.getCatchUpCost();
        if (cost > 0) {
            PointAccount account = pointService.getBalance(userId);
            if (account.getBalance() < cost) {
                return SignInResult.fail("积分不足，补签需要" + cost + "积分");
            }
            pointService.deduct(userId, cost, SOURCE_CATCH_UP, configId, "补签扣减: " + config.getName());
        }

        // Create catch-up record
        String periodKey = resolvePeriodKey(config.getPeriodType(), targetDate);
        int streakDay = calculateStreak(configId, userId, targetDate, config.getPeriodType(), periodKey);

        StreakTierConfig tierConfig = parseStreakConfig(config.getStreakConfig());
        if (tierConfig != null && tierConfig.getMaxStreak() != null) {
            streakDay = Math.min(streakDay, tierConfig.getMaxStreak());
        }

        int basePoints = config.getBasePoints();
        int bonusPoints = 0;
        Integer tierReached = null;
        if (tierConfig != null && tierConfig.getTiers() != null) {
            for (StreakTierConfig.Tier tier : tierConfig.getTiers()) {
                if (streakDay >= tier.getDay()) {
                    bonusPoints = tier.getBonus();
                    tierReached = tier.getDay();
                }
            }
        }
        int totalPoints = basePoints + bonusPoints;

        SignInRecord record = new SignInRecord();
        record.setConfigId(configId);
        record.setUserId(userId);
        record.setSigninDate(targetDate);
        record.setPeriodKey(periodKey);
        record.setStreakDay(streakDay);
        record.setBasePoints(basePoints);
        record.setBonusPoints(bonusPoints);
        record.setTotalPoints(totalPoints);
        record.setTierReached(tierReached);
        record.setCatchUp(true);
        recordMapper.insert(record);

        // Recalculate streaks for affected records in this period
        recalculateStreaks(configId, userId, periodKey, config, tierConfig);

        // Grant points
        LocalDateTime expireAt = calculateExpireAt(config.getPointExpireDays());
        pointService.earn(userId, totalPoints, SOURCE_SIGNIN, record.getId(), expireAt,
                "补签签到: " + config.getName());

        PointAccount account = pointService.getBalance(userId);
        log.info("补签成功: userId={}, configId={}, date={}, streak={}, points={}", userId, configId, targetDate, streakDay, totalPoints);
        return SignInResult.ok(record.getId(), streakDay, basePoints, bonusPoints,
                totalPoints, tierReached, account.getBalance(), true);
    }

    public SignInCalendarVO getCalendar(Long configId, String userId, String periodKey) {
        requireConfig(configId);

        List<SignInRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<SignInRecord>()
                        .eq(SignInRecord::getConfigId, configId)
                        .eq(SignInRecord::getUserId, userId)
                        .eq(SignInRecord::getPeriodKey, periodKey)
                        .orderByAsc(SignInRecord::getSigninDate));

        Map<LocalDate, SignInRecord> recordMap = records.stream()
                .collect(Collectors.toMap(SignInRecord::getSigninDate, r -> r));

        LocalDate periodStart = parsePeriodStart(periodKey);
        LocalDate periodEnd = parsePeriodEnd(periodKey);
        LocalDate today = LocalDate.now();
        if (periodEnd.isAfter(today)) {
            periodEnd = today;
        }

        List<SignInCalendarVO.DayEntry> days = new ArrayList<>();
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            SignInRecord r = recordMap.get(date);
            if (r != null) {
                days.add(new SignInCalendarVO.DayEntry(date, true, r.getStreakDay(), r.getTotalPoints(), r.getCatchUp()));
            } else {
                days.add(new SignInCalendarVO.DayEntry(date, false, null, null, false));
            }
        }

        int currentStreak = records.isEmpty() ? 0 :
                records.get(records.size() - 1).getStreakDay();
        return new SignInCalendarVO(periodKey, currentStreak, records.size(), days);
    }

    public SignInStatusVO getStatus(Long configId, String userId) {
        SignInConfig config = requireConfig(configId);
        LocalDate today = LocalDate.now();
        String periodKey = resolvePeriodKey(config.getPeriodType(), today);

        boolean todaySigned = findRecord(configId, userId, today) != null;

        List<SignInRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<SignInRecord>()
                        .eq(SignInRecord::getConfigId, configId)
                        .eq(SignInRecord::getUserId, userId)
                        .eq(SignInRecord::getPeriodKey, periodKey)
                        .orderByAsc(SignInRecord::getSigninDate));

        int currentStreak = records.isEmpty() ? 0 :
                records.get(records.size() - 1).getStreakDay();

        PointAccount account = pointService.getBalance(userId);

        // Find next tier
        StreakTierConfig tierConfig = parseStreakConfig(config.getStreakConfig());
        Integer nextTierDay = null;
        Integer nextTierBonus = null;
        if (tierConfig != null && tierConfig.getTiers() != null) {
            for (StreakTierConfig.Tier tier : tierConfig.getTiers()) {
                if (tier.getDay() > currentStreak) {
                    nextTierDay = tier.getDay();
                    nextTierBonus = tier.getBonus();
                    break;
                }
            }
        }

        return new SignInStatusVO(todaySigned, currentStreak, records.size(),
                account.getBalance(), nextTierDay, nextTierBonus);
    }

    public List<SignInConfig> listActiveConfigs() {
        LocalDateTime now = LocalDateTime.now();
        return configMapper.selectList(
                new LambdaQueryWrapper<SignInConfig>()
                        .eq(SignInConfig::getStatus, SignInConfigStatus.PUBLISHED.name())
                        .and(w -> w.isNull(SignInConfig::getStartTime).or().le(SignInConfig::getStartTime, now))
                        .and(w -> w.isNull(SignInConfig::getEndTime).or().ge(SignInConfig::getEndTime, now)));
    }

    // --- Private helpers ---

    private int calculateStreak(Long configId, String userId, LocalDate date,
                                String periodType, String periodKey) {
        List<SignInRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<SignInRecord>()
                        .eq(SignInRecord::getConfigId, configId)
                        .eq(SignInRecord::getUserId, userId)
                        .eq(SignInRecord::getPeriodKey, periodKey)
                        .lt(SignInRecord::getSigninDate, date)
                        .orderByDesc(SignInRecord::getSigninDate)
                        .last("LIMIT 1"));

        if (records.isEmpty()) {
            // Check carry-over from previous period
            String prevPeriodKey = resolvePreviousPeriodKey(periodType, date);
            if (prevPeriodKey != null) {
                List<SignInRecord> prevRecords = recordMapper.selectList(
                        new LambdaQueryWrapper<SignInRecord>()
                                .eq(SignInRecord::getConfigId, configId)
                                .eq(SignInRecord::getUserId, userId)
                                .eq(SignInRecord::getPeriodKey, prevPeriodKey)
                                .orderByDesc(SignInRecord::getSigninDate)
                                .last("LIMIT 1"));
                if (!prevRecords.isEmpty()) {
                    LocalDate lastPrevDate = prevRecords.get(0).getSigninDate();
                    if (lastPrevDate.plusDays(1).equals(date)) {
                        return prevRecords.get(0).getStreakDay() + 1;
                    }
                }
            }
            return 1;
        }

        SignInRecord lastRecord = records.get(0);
        if (lastRecord.getSigninDate().plusDays(1).equals(date)) {
            return lastRecord.getStreakDay() + 1;
        }
        return 1;
    }

    private void recalculateStreaks(Long configId, String userId, String periodKey,
                                    SignInConfig config, StreakTierConfig tierConfig) {
        List<SignInRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<SignInRecord>()
                        .eq(SignInRecord::getConfigId, configId)
                        .eq(SignInRecord::getUserId, userId)
                        .eq(SignInRecord::getPeriodKey, periodKey)
                        .orderByAsc(SignInRecord::getSigninDate));

        int streak = 0;
        LocalDate prevDate = null;
        int maxStreak = tierConfig != null && tierConfig.getMaxStreak() != null
                ? tierConfig.getMaxStreak() : Integer.MAX_VALUE;

        for (SignInRecord r : records) {
            if (prevDate == null || r.getSigninDate().equals(prevDate.plusDays(1))) {
                streak++;
            } else {
                streak = 1;
            }
            streak = Math.min(streak, maxStreak);

            int oldStreak = r.getStreakDay();
            if (streak != oldStreak) {
                r.setStreakDay(streak);

                int bonusPoints = 0;
                Integer tierReached = null;
                if (tierConfig != null && tierConfig.getTiers() != null) {
                    for (StreakTierConfig.Tier tier : tierConfig.getTiers()) {
                        if (streak >= tier.getDay()) {
                            bonusPoints = tier.getBonus();
                            tierReached = tier.getDay();
                        }
                    }
                }
                r.setBonusPoints(bonusPoints);
                r.setTotalPoints(r.getBasePoints() + bonusPoints);
                r.setTierReached(tierReached);
                recordMapper.updateById(r);
            }
            prevDate = r.getSigninDate();
        }
    }

    private String resolvePeriodKey(String periodType, LocalDate date) {
        if ("WEEKLY".equals(periodType)) {
            int weekOfYear = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            int year = date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
            return String.format("%d-W%02d", year, weekOfYear);
        } else {
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
    }

    private String resolvePreviousPeriodKey(String periodType, LocalDate date) {
        LocalDate prevDate = date.minusDays(1);
        if ("WEEKLY".equals(periodType)) {
            if (date.getDayOfWeek() == DayOfWeek.MONDAY) {
                return resolvePeriodKey(periodType, prevDate);
            }
            return null;
        } else {
            if (date.getDayOfMonth() == 1) {
                return resolvePeriodKey(periodType, prevDate);
            }
            return null;
        }
    }

    private LocalDate parsePeriodStart(String periodKey) {
        if (periodKey.contains("-W")) {
            String[] parts = periodKey.split("-W");
            int year = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            return LocalDate.ofYearDay(year, 1)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .plusWeeks(week - 1);
        } else {
            return LocalDate.parse(periodKey + "-01");
        }
    }

    private LocalDate parsePeriodEnd(String periodKey) {
        LocalDate start = parsePeriodStart(periodKey);
        if (periodKey.contains("-W")) {
            return start.plusDays(6);
        } else {
            return start.with(TemporalAdjusters.lastDayOfMonth());
        }
    }

    private SignInRecord findRecord(Long configId, String userId, LocalDate date) {
        return recordMapper.selectOne(
                new LambdaQueryWrapper<SignInRecord>()
                        .eq(SignInRecord::getConfigId, configId)
                        .eq(SignInRecord::getUserId, userId)
                        .eq(SignInRecord::getSigninDate, date));
    }

    private SignInConfig requireConfig(Long configId) {
        SignInConfig config = configMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "签到活动不存在");
        }
        return config;
    }

    private void validateConfig(SignInConfig config) {
        if (!SignInConfigStatus.PUBLISHED.name().equals(config.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "签到活动未发布");
        }
        LocalDateTime now = LocalDateTime.now();
        if (config.getStartTime() != null && now.isBefore(config.getStartTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "签到活动未开始");
        }
        if (config.getEndTime() != null && now.isAfter(config.getEndTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "签到活动已结束");
        }
    }

    private StreakTierConfig parseStreakConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StreakTierConfig.class);
        } catch (JsonProcessingException e) {
            log.warn("解析 streakConfig 失败: {}", json, e);
            return null;
        }
    }

    private LocalDateTime calculateExpireAt(Integer expireDays) {
        if (expireDays == null || expireDays <= 0) {
            return null;
        }
        return LocalDateTime.now().plusDays(expireDays);
    }
}
