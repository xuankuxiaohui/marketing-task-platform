package com.marketing.prize.service.handlers;

import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.prize.domain.config.CouponParams;
import com.marketing.prize.domain.entity.Prize;
import com.marketing.prize.domain.entity.PrizeRecord;
import com.marketing.prize.domain.enums.PrizeType;
import com.marketing.prize.service.GrantResult;
import com.marketing.prize.service.PrizeHandler;
import com.marketing.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component("couponPrizeHandler")
public class CouponPrizeHandler implements PrizeHandler {

    @Override
    public PrizeType supports() { return PrizeType.COUPON; }

    @Override
    public void validate(Prize prize) {
        try {
            CouponParams params = JsonUtil.jsonToObjV2(prize.getParamsJson(), CouponParams.class);
            if (params.getTemplateId() == null || params.getTemplateId().isBlank()) {
                throw new BusinessException(ErrorCode.PRIZE_HANDLER_VALIDATION, "优惠券模板ID不能为空");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PRIZE_HANDLER_VALIDATION, "优惠券参数校验失败: " + e.getMessage());
        }
    }

    @Override
    public GrantResult grant(PrizeRecord record, Prize prize) {
        try {
            CouponParams params = JsonUtil.jsonToObjV2(record.getPrizeParamsJson(), CouponParams.class);
            log.info("[CouponPrize] 发放优惠券 userId={}, templateId={}, amount={}, recordId={}",
                    record.getUserId(), params.getTemplateId(), params.getAmount(), record.getId());
            // 调用外部优惠券系统API
            String tradeNo = "CPN-" + UUID.randomUUID().toString().substring(0, 8);
            return GrantResult.success(tradeNo);
        } catch (Exception e) {
            log.error("优惠券发放失败, recordId={}", record.getId(), e);
            throw new BusinessException(ErrorCode.PRIZE_HANDLER_ERROR, "优惠券发放失败，请联系客服");
        }
    }
}
