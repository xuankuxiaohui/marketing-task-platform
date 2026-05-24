package com.marketing.task.prize.service.handlers;

import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.prize.domain.config.BadgeParams;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.domain.entity.PrizeRecord;
import com.marketing.task.prize.domain.enums.PrizeType;
import com.marketing.task.prize.service.GrantResult;
import com.marketing.task.prize.service.PrizeHandler;
import com.marketing.task.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component("badgePrizeHandler")
public class BadgePrizeHandler implements PrizeHandler {

    @Override
    public PrizeType supports() { return PrizeType.BADGE; }

    @Override
    public void validate(Prize prize) {
        try {
            BadgeParams params = JsonUtil.jsonToObjV2(prize.getParamsJson(), BadgeParams.class);
            if (params.getBadgeId() == null || params.getBadgeId().isBlank()) {
                throw new BusinessException(ErrorCode.PRIZE_HANDLER_VALIDATION, "徽章ID不能为空");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PRIZE_HANDLER_VALIDATION, "徽章参数校验失败: " + e.getMessage());
        }
    }

    @Override
    public GrantResult grant(PrizeRecord record, Prize prize) {
        try {
            BadgeParams params = JsonUtil.jsonToObjV2(record.getPrizeParamsJson(), BadgeParams.class);
            log.info("[BadgePrize] 发放徽章 userId={}, badgeId={}, name={}, recordId={}",
                    record.getUserId(), params.getBadgeId(), params.getName(), record.getId());
            // 调用外部徽章系统API
            String tradeNo = "BDG-" + UUID.randomUUID().toString().substring(0, 8);
            return GrantResult.success(tradeNo);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PRIZE_HANDLER_ERROR, "徽章发放失败: " + e.getMessage());
        }
    }
}
