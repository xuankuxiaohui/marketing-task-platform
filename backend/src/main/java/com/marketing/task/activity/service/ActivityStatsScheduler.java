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

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityStatsScheduler {

    private final ActivityMapper activityMapper;
    private final ActivityStatsMapper statsMapper;

    @Scheduled(cron = "0 * * * * ?")
    public void autoOnline() {
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.PUBLISHED.name())
                        .eq(Activity::getDeleted, 0)
                        .le(Activity::getStartTime, LocalDateTime.now()));
        for (Activity activity : activities) {
            activity.setStatus(ActivityStatus.ONLINE.name());
            activity.setUpdatedAt(LocalDateTime.now());
            activityMapper.updateById(activity);
            log.info("活动自动上线: id={}, code={}", activity.getId(), activity.getCode());
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void autoOffline() {
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.ONLINE.name())
                        .eq(Activity::getDeleted, 0)
                        .le(Activity::getEndTime, LocalDateTime.now()));
        for (Activity activity : activities) {
            activity.setStatus(ActivityStatus.OFFLINE.name());
            activity.setUpdatedAt(LocalDateTime.now());
            activityMapper.updateById(activity);
            log.info("活动自动下线: id={}, code={}", activity.getId(), activity.getCode());
        }
    }
}
