package com.mkt.risk.application;

import com.mkt.risk.entity.RiskListItemEntity;
import com.mkt.risk.mapper.RiskListItemMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisRiskListItemStore implements RiskListItemStore {

    private final RiskListItemMapper mapper;

    public MybatisRiskListItemStore(RiskListItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RiskListItemEntity getByUk(String dimension, String listType, String listValue) {
        return mapper.getByUk(dimension, listType, listValue);
    }

    @Override
    public List<RiskListItemEntity> listByUks(List<RiskListUk> uks) {
        if (uks == null || uks.isEmpty()) {
            return List.of();
        }
        return mapper.listByUks(uks);
    }

    @Override
    public int insert(RiskListItemEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public RiskListItemEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public int deleteById(long id) {
        return mapper.deleteById(id);
    }

    @Override
    public long countByQuery(
            String dimension, String listType, String listValue, LocalDateTime from, LocalDateTime to) {
        return mapper.countByQuery(dimension, listType, listValue, from, to);
    }

    @Override
    public List<RiskListItemEntity> listByQuery(
            String dimension,
            String listType,
            String listValue,
            LocalDateTime from,
            LocalDateTime to,
            long offset,
            int limit) {
        return mapper.listByQuery(dimension, listType, listValue, from, to, offset, limit);
    }
}
