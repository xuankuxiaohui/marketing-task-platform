package com.marketing.prize.service.handlers;

import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.prize.domain.config.PointParams;
import com.marketing.prize.domain.entity.Prize;
import com.marketing.prize.domain.entity.PrizeRecord;
import com.marketing.prize.domain.enums.PrizeType;
import com.marketing.prize.service.GrantResult;
import com.marketing.prize.service.PrizeHandler;
import com.marketing.signin.service.PointService;
import com.marketing.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component("pointPrizeHandler")
@RequiredArgsConstructor
public class PointPrizeHandler implements PrizeHandler {
    private final PointService pointService;

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
            LocalDateTime expireAt = record.getExpireTime();
            String reason = params.getReason() != null ? params.getReason() : prize.getName();

            pointService.earn(record.getUserId(), amount, "TASK_REWARD",
                    record.getId(), expireAt, "任务奖励: " + reason);

            log.info("[PointPrize] 积分入账 userId={}, amount={}, recordId={}",
                    record.getUserId(), amount, record.getId());
            return GrantResult.success("PNT-" + record.getId());
        } catch (Exception e) {
            log.error("积分发放失败, recordId={}", record.getId(), e);
            throw new BusinessException(ErrorCode.PRIZE_HANDLER_ERROR, "积分发放失败，请联系客服");
        }
    }

    @Override
    public Optional<Long> queryBalance(String userId) {
        return Optional.of(pointService.getBalance(userId).getBalance());
    }
}
