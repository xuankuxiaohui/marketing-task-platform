package com.marketing.task.prize.service.handlers;

import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.prize.domain.config.PointParams;
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
@Component("pointPrizeHandler")
public class PointPrizeHandler implements PrizeHandler {

    @Override
    public PrizeType supports() { return PrizeType.POINT; }

    @Override
    public void validate(Prize prize) {
        try {
            PointParams params = JsonUtil.jsonToObjV2(prize.getParamsJson(), PointParams.class);
            if (params.getAmount() <= 0) {
                throw new BusinessException(ErrorCode.PRIZE_HANDLER_VALIDATION, "积分数量必须大于0");
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PRIZE_HANDLER_VALIDATION, "积分参数校验失败: " + e.getMessage());
        }
    }

    @Override
    public GrantResult grant(PrizeRecord record, Prize prize) {
        try {
            PointParams params = JsonUtil.jsonToObjV2(record.getPrizeParamsJson(), PointParams.class);
            int amount = params.getAmount() * record.getQuantity();
            log.info("[PointPrize] 发放积分 userId={}, amount={}, recordId={}",
                    record.getUserId(), amount, record.getId());
            // 内部逻辑：积分账户由本系统管理，直接入账
            // TODO: 对接真实积分账户系统后替换
            return GrantResult.success("PNT-" + UUID.randomUUID().toString().substring(0, 8));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PRIZE_HANDLER_ERROR, "积分发放失败: " + e.getMessage());
        }
    }
}
