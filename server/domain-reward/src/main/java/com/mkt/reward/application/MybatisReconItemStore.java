package com.mkt.reward.application;

import com.mkt.reward.entity.ReconItemEntity;
import com.mkt.reward.mapper.ReconItemMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisReconItemStore implements ReconItemStore {

    private final ReconItemMapper mapper;

    public MybatisReconItemStore(ReconItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ReconItemEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public int insert(ReconItemEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(ReconItemEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public List<ReconItemEntity> list(long batchId, String result, String reviewStatus, long offset, int limit) {
        return mapper.listByBatch(batchId, result, reviewStatus, offset, limit);
    }

    @Override
    public long count(long batchId, String result, String reviewStatus) {
        return mapper.countByBatch(batchId, result, reviewStatus);
    }

    @Override
    public List<ReconItemEntity> listAll(long batchId) {
        return mapper.listAllByBatch(batchId);
    }

    @Override
    public int casAction(ReconItemEntity entity) {
        return mapper.casAction(entity);
    }

    @Override
    public int casReview(ReconItemEntity entity) {
        return mapper.casReview(entity);
    }
}
