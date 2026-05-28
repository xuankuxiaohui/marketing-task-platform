package com.marketing.task.activity.service;

import com.marketing.task.activity.checker.ParticipationChecker;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.ActivityStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ActivityRuleService {

    private final List<ParticipationChecker> checkers;

    public ActivityRuleService(List<ParticipationChecker> checkers) {
        this.checkers = checkers;
    }

    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        if (!ActivityStatus.ONLINE.name().equals(activity.getStatus())) {
            return RuleCheckResult.fail("ACTIVITY_NOT_ONLINE", "活动未在进行中", "STATUS");
        }

        for (ParticipationChecker checker : checkers) {
            RuleCheckResult result = checker.check(activity, context);
            if (!result.isPassed()) {
                log.info("活动规则校验失败: activityCode={}, checker={}, failCode={}",
                        activity.getCode(), result.getCheckerType(), result.getFailCode());
                return result;
            }
        }

        return RuleCheckResult.pass();
    }
}
