package com.marketing.prize.service.handlers;

import com.marketing.prize.domain.entity.Prize;
import com.marketing.prize.domain.entity.PrizeRecord;
import com.marketing.prize.domain.enums.PrizeType;
import com.marketing.prize.service.GrantResult;
import com.marketing.prize.service.PrizeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component("internalPrizeHandler")
public class InternalPrizeHandler implements PrizeHandler {

    @Override
    public PrizeType supports() { return PrizeType.INTERNAL; }

    @Override
    public void validate(Prize prize) {
        // 内部奖品无额外校验
    }

    @Override
    public GrantResult grant(PrizeRecord record, Prize prize) {
        log.info("[InternalPrize] 发放内部奖品 userId={}, prizeName={}, recordId={}",
                record.getUserId(), record.getPrizeName(), record.getId());
        return GrantResult.success("INT-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
