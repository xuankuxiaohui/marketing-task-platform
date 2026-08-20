package com.mkt.reward.application;

import com.mkt.reward.entity.GrantRecordEntity;
import java.time.LocalDateTime;
import java.util.List;

public interface GrantRecordStore {

    GrantRecordEntity getById(long id);

    GrantRecordEntity getByIdempotent(String grantSource, String sourceId, long prizeId);

    int insert(GrantRecordEntity entity);

    int update(GrantRecordEntity entity);

    /** CAS: persist {@code entity} only if the stored row is still {@code expectedStatus}. */
    int updateIfStatus(GrantRecordEntity entity, String expectedStatus);

    int updateFulfillmentRef(long id, String fulfillmentRef);

    long countActive(long userId, long prizeId, LocalDateTime from);

    int markPermanentFailed(long id);

    List<GrantRecordEntity> listDueRetry(LocalDateTime now, int limit);
}
