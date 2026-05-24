package com.marketing.task.prize.service;

import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.domain.entity.PrizeRecord;
import com.marketing.task.prize.domain.enums.PrizeType;

import java.util.Optional;

public interface PrizeHandler {
    PrizeType supports();
    void validate(Prize prize);
    GrantResult grant(PrizeRecord record, Prize prize);

    default Optional<Long> queryBalance(String userId) {
        return Optional.empty();
    }
}
