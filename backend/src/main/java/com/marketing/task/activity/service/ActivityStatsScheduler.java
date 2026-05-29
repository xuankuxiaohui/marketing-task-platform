package com.marketing.task.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.ActivityStatus;
import com.marketing.task.activity.mapper.ActivityMapper;
import com.marketing.task.activity.mapper.ActivityStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityStatsScheduler {

    private final ActivityMapper activityMapper;
    private final ActivityStatsMapper statsMapper;
    private final ActivityCacheService cacheService;

    @Transactional
    @Scheduled(cron = "0 * * * * ?")
    public void autoTransition() {
        LocalDateTime now = LocalDateTime.now();

        List<Activity> toOnline = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.PUBLISHED.name())
                        .eq(Activity::getDeleted, 0)
                        .le(Activity::getStartTime, now));
        for (Activity activity : toOnline) {
            activity.setStatus(ActivityStatus.ONLINE.name());
            activity.setUpdatedAt(now);
            activityMapper.updateById(activity);
            cacheService.evictActivity(activity.getId());
            log.info("活动自动上线: id={}, code={}", activity.getId(), activity.getCode());
        }

        List<Activity> toOffline = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.ONLINE.name())
                        .eq(Activity::getDeleted, 0)
                        .le(Activity::getEndTime, now));
        for (Activity activity : toOffline) {
            activity.setStatus(ActivityStatus.OFFLINE.name());
            activity.setUpdatedAt(now);
            activityMapper.updateById(activity);
            cacheService.evictActivity(activity.getId());
            log.info("活动自动下线: id={}, code={}", activity.getId(), activity.getCode());
        }
    }
}
