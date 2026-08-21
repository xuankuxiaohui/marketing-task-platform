package com.mkt.reward.application;

import com.mkt.reward.entity.PntAccountEntity;
import com.mkt.reward.entity.PntTransactionEntity;
import java.time.LocalDateTime;
import java.util.List;

public interface PointsStore {

    int insertIgnoreAccount(long userId);

    PntAccountEntity getByUserId(long userId);

    PntAccountEntity lockByUserId(long userId);

    int applyDelta(long userId, long delta);

    int insertTx(PntTransactionEntity row);

    boolean expireExists(String bizId);

    List<PntTransactionEntity> listExpireDue(LocalDateTime now, int limit);

    long countAccounts(Long userId);

    List<PntAccountEntity> listAccounts(Long userId, long offset, int limit);

    long countTx(Long userId, String type, LocalDateTime from, LocalDateTime to);

    List<PntTransactionEntity> listTx(
            Long userId, String type, LocalDateTime from, LocalDateTime to, long offset, int limit);
}
