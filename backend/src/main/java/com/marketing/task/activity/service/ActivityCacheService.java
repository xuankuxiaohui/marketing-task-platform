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

    private final Cache<String, Activity> activityByCodeCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    private final Cache<String, ActivityDisplayRule> ruleCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public Activity getActivity(Long id) {
        return activityCache.get(id, key -> activityMapper.selectById(key));
    }

    public Activity getActivityByCode(String code) {
        return activityByCodeCache.get(code, key -> activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getCode, key)
                        .eq(Activity::getDeleted, 0)));
    }

    public ActivityDisplayRule getDisplayRule(String activityCode) {
        return ruleCache.get(activityCode, key -> displayRuleMapper.selectOne(
                new LambdaQueryWrapper<ActivityDisplayRule>()
                        .eq(ActivityDisplayRule::getActivityCode, key)));
    }

    public void evictActivity(Long id) {
        Activity cached = activityCache.getIfPresent(id);
        activityCache.invalidate(id);
        if (cached != null) {
            activityByCodeCache.invalidate(cached.getCode());
        }
        log.info("清除活动缓存: id={}", id);
    }

    public void evictDisplayRule(String activityCode) {
        ruleCache.invalidate(activityCode);
        log.info("清除展示规则缓存: activityCode={}", activityCode);
    }
}
