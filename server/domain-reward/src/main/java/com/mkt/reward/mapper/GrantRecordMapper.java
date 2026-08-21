package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.GrantRecordEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GrantRecordMapper extends BaseMapper<GrantRecordEntity> {

    long countActiveByUserPrize(
            @Param("userId") long userId,
            @Param("prizeId") long prizeId,
            @Param("from") LocalDateTime from);

    int markPermanentFailed(@Param("id") long id);

    GrantRecordEntity selectByIdempotent(
            @Param("grantSource") String grantSource,
            @Param("sourceId") String sourceId,
            @Param("prizeId") long prizeId);

    int updateIfStatus(
            @Param("row") GrantRecordEntity row, @Param("expectedStatus") String expectedStatus);

    int updateFulfillmentRef(@Param("id") long id, @Param("fulfillmentRef") String fulfillmentRef);

    List<GrantRecordEntity> listDueRetry(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int casClaimStart(
            @Param("id") long id,
            @Param("now") LocalDateTime now,
            @Param("retryMax") int retryMax);

    int casExpireOne(@Param("id") long id, @Param("now") LocalDateTime now);

    int casPermanentFromRetry(@Param("id") long id, @Param("now") LocalDateTime now, @Param("retryMax") int retryMax);

    GrantRecordEntity selectByFulfillmentRef(@Param("fulfillmentRef") String fulfillmentRef);

    int updateIfFulfillment(
            @Param("row") GrantRecordEntity row, @Param("expectedFulfillment") String expectedFulfillment);

    int closeAsManual(
            @Param("id") long id,
            @Param("now") LocalDateTime now);

    List<GrantRecordEntity> listClaimingTimeout(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    int rollbackClaiming(
            @Param("id") long id, @Param("now") LocalDateTime now);

    List<GrantRecordEntity> listPrizeExpireDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    List<GrantRecordEntity> listFulfillRetryDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    List<GrantRecordEntity> listSendingTimeout(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    List<GrantRecordEntity> listCrossDaySending(
            @Param("dayStart") LocalDateTime dayStart, @Param("limit") int limit);

    int markReconPending(@Param("id") long id, @Param("now") LocalDateTime now);

    List<GrantRecordEntity> listPlatformRecon(
            @Param("categoryCode") String categoryCode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    List<GrantRecordEntity> listPortalPrizes(
            @Param("userId") long userId,
            @Param("pendingOnly") boolean pendingOnly,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countPortalPrizes(@Param("userId") long userId, @Param("pendingOnly") boolean pendingOnly);

    List<com.mkt.reward.response.SpendRowView> sumSpend(
            @Param("categoryCode") String categoryCode,
            @Param("prizeId") Long prizeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    long countByUserStatus(@Param("userId") long userId, @Param("status") String status);
}
