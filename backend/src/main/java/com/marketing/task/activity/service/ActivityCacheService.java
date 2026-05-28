package com.marketing.task.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.mapper.ActivityDisplayRuleMapper;
import com.marketing.task.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityCacheService {

    private final ActivityMapper activityMapper;
    private final ActivityDisplayRuleMapper displayRuleMapper;

    private final Cache<Long, Activity> activityCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    private final Cache<Long, ActivityDisplayRule> ruleCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public Activity getActivity(Long id) {
        return activityCache.get(id, key -> activityMapper.selectById(key));
    }

    public Activity getActivityByCode(String code) {
        return activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getCode, code)
                        .eq(Activity::getDeleted, 0));
    }

    public ActivityDisplayRule getDisplayRule(Long activityId) {
        return ruleCache.get(activityId, key -> displayRuleMapper.selectById(key));
    }

    public void evictActivity(Long id) {
        activityCache.invalidate(id);
        log.info("清除活动缓存: id={}", id);
    }

    public void evictDisplayRule(Long activityId) {
        ruleCache.invalidate(activityId);
        log.info("清除展示规则缓存: activityId={}", activityId);
    }
}
