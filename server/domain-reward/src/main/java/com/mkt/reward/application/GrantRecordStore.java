package com.mkt.reward.application;

import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.response.SpendRowView;
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

    int casClaimStart(long id, LocalDateTime now, int retryMax);

    int casExpireOne(long id, LocalDateTime now);

    int casPermanentFromRetry(long id, LocalDateTime now, int retryMax);

    GrantRecordEntity getByFulfillmentRef(String fulfillmentRef);

    int updateIfFulfillment(GrantRecordEntity entity, String expectedFulfillment);

    int closeAsManual(long id, LocalDateTime now);

    List<GrantRecordEntity> listClaimingTimeout(LocalDateTime cutoff, int limit);

    int rollbackClaiming(long id, LocalDateTime now);

    List<GrantRecordEntity> listPrizeExpireDue(LocalDateTime now, int limit);

    List<GrantRecordEntity> listFulfillRetryDue(LocalDateTime now, int limit);

    List<GrantRecordEntity> listSendingTimeout(LocalDateTime cutoff, int limit);

    List<GrantRecordEntity> listCrossDaySending(LocalDateTime dayStart, int limit);

    int markReconPending(long id, LocalDateTime now);

    List<GrantRecordEntity> listPlatformRecon(String categoryCode, LocalDateTime from, LocalDateTime to);

    List<GrantRecordEntity> listPortalPrizes(long userId, boolean pendingOnly, long offset, int limit);

    long countPortalPrizes(long userId, boolean pendingOnly);

    List<SpendRowView> sumSpend(String categoryCode, Long prizeId, LocalDateTime from, LocalDateTime to);

    long countByUserStatus(long userId, String status);
}
