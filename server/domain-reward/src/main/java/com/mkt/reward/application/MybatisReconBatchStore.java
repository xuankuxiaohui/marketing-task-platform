package com.mkt.reward.application;

import com.mkt.reward.entity.ReconBatchEntity;
import com.mkt.reward.mapper.ReconBatchMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisReconBatchStore implements ReconBatchStore {

    private final ReconBatchMapper mapper;

    public MybatisReconBatchStore(ReconBatchMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ReconBatchEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public ReconBatchEntity getByCategoryAndDay(String categoryCode, LocalDate billDate) {
        return mapper.selectByCategoryAndDay(categoryCode, billDate);
    }

    @Override
    public int insert(ReconBatchEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(ReconBatchEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public List<ReconBatchEntity> list(
            String categoryCode, LocalDate billDate, String status, long offset, int limit) {
        return mapper.listByFilter(categoryCode, billDate, status, offset, limit);
    }

    @Override
    public long count(String categoryCode, LocalDate billDate, String status) {
        return mapper.countByFilter(categoryCode, billDate, status);
    }
}
