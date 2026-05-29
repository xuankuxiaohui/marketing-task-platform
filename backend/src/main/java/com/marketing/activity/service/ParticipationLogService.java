package com.marketing.activity.service;

import com.marketing.activity.domain.dto.RuleCheckResult;
import com.marketing.activity.domain.entity.ParticipationLog;
import com.marketing.activity.mapper.ActivityStatsMapper;
import com.marketing.activity.mapper.ParticipationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationLogService {

    private final ParticipationLogMapper participationLogMapper;
    private final ActivityStatsMapper activityStatsMapper;

    @Transactional
    public void recordParticipation(String activityCode, long userId, String clientIp, RuleCheckResult result) {
        ParticipationLog logEntry = new ParticipationLog();
        logEntry.setActivityCode(activityCode);
        logEntry.setUserId(userId);
        logEntry.setClientIp(clientIp);
        logEntry.setCheckerResult(result.isPassed() ? "PASS" : "FAIL");
        if (!result.isPassed()) {
            logEntry.setFailCode(result.getFailCode());
            logEntry.setFailMessage(result.getFailMessage());
        }
        try {
            participationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("记录活动参与日志失败: activityCode={}, userId={}", activityCode, userId, e);
        }

        if (result.isPassed()) {
            try {
                activityStatsMapper.incrementParticipantCount(activityCode);
            } catch (Exception e) {
                log.warn("递增活动参与计数失败: activityCode={}", activityCode, e);
            }
        }
    }
}
