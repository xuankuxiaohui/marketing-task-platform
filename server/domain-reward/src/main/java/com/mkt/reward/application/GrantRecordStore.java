package com.mkt.reward.application;

import com.mkt.reward.entity.GrantRecordEntity;
import java.time.LocalDateTime;

public interface GrantRecordStore {

    GrantRecordEntity getById(long id);

    int insert(GrantRecordEntity entity);

    long countActive(long userId, long prizeId, LocalDateTime from);

    int markPermanentFailed(long id);
}
