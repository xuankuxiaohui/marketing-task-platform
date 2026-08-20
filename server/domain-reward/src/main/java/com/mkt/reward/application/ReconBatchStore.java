package com.mkt.reward.application;

import com.mkt.reward.entity.ReconBatchEntity;
import java.time.LocalDate;
import java.util.List;

public interface ReconBatchStore {

    ReconBatchEntity getById(long id);

    ReconBatchEntity getByCategoryAndDay(String categoryCode, LocalDate billDate);

    int insert(ReconBatchEntity entity);

    int update(ReconBatchEntity entity);

    List<ReconBatchEntity> list(String categoryCode, LocalDate billDate, String status, long offset, int limit);

    long count(String categoryCode, LocalDate billDate, String status);
}
