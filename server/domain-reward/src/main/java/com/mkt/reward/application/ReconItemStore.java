package com.mkt.reward.application;

import com.mkt.reward.entity.ReconItemEntity;
import java.util.List;

public interface ReconItemStore {

    ReconItemEntity getById(long id);

    int insert(ReconItemEntity entity);

    int update(ReconItemEntity entity);

    List<ReconItemEntity> list(long batchId, String result, String reviewStatus, long offset, int limit);

    long count(long batchId, String result, String reviewStatus);

    List<ReconItemEntity> listAll(long batchId);

    int casAction(ReconItemEntity entity);

    int casReview(ReconItemEntity entity);
}
