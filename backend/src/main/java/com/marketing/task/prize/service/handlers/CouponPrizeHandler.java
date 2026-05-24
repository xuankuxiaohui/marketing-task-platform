package com.marketing.task.prize.service.handlers;

import com.marketing.task.common.BusinessException;
import com.marketing.task.prize.domain.config.CouponParams;
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
@Component("couponPrizeHandler")
public class CouponPrizeHandler implements PrizeHandler {

    @Override
    public PrizeType supports() { return PrizeType.COUPON; }

    @Override
    public void validate(Prize prize) {
        try {
            CouponParams params = JsonUtil.jsonToObjV2(prize.getParamsJson(), CouponParams.class);
            if (params.getTemplateId() == null || params.getTemplateId().isBlank()) {
                throw new BusinessException("优惠券模板ID不能为空");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("优惠券参数校验失败: " + e.getMessage());
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
            throw new BusinessException("优惠券发放失败: " + e.getMessage());
        }
    }
}
