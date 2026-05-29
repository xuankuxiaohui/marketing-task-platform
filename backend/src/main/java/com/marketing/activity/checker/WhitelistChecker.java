package com.marketing.activity.checker;

import com.marketing.activity.domain.dto.CheckerConfig;
import com.marketing.activity.domain.dto.ParticipationContext;
import com.marketing.activity.domain.dto.RuleCheckResult;
import com.marketing.activity.domain.entity.Activity;
import com.marketing.activity.domain.enums.CheckerType;
import com.marketing.task.domain.entity.ListData;
import com.marketing.task.mapper.ListDataMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@RequiredArgsConstructor
public class WhitelistChecker extends AbstractParticipationChecker {

    private final ListDataMapper listDataMapper;

    @Override
    public String checkerType() {
        return CheckerType.WHITELIST.name();
    }

    @Override
    protected RuleCheckResult doCheck(Activity activity, ParticipationContext context, CheckerConfig config) {
        String listCode = (String) config.getParams().get("listCode");
        if (listCode == null) return RuleCheckResult.pass();

        long count = listDataMapper.selectCount(
                new LambdaQueryWrapper<ListData>()
                        .eq(ListData::getListKey, listCode)
                        .eq(ListData::getUserId, String.valueOf(context.getUserId()))
        );
        if (count == 0) {
            return RuleCheckResult.fail("NOT_IN_WHITELIST", "不在活动白名单中", checkerType());
        }
        return RuleCheckResult.pass();
    }
}
