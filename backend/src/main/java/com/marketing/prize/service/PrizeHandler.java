package com.marketing.prize.service;

import com.marketing.prize.domain.entity.Prize;
import com.marketing.prize.domain.entity.PrizeRecord;
import com.marketing.prize.domain.enums.PrizeType;

import java.util.Optional;

public interface PrizeHandler {
    PrizeType supports();
    void validate(Prize prize);
    GrantResult grant(PrizeRecord record, Prize prize);

    default Optional<Long> queryBalance(String userId) {
        return Optional.empty();
    }
}
