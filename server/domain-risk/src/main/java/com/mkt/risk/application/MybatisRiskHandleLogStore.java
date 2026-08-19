package com.mkt.risk.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mkt.risk.entity.RiskHandleLogEntity;
import com.mkt.risk.mapper.RiskHandleLogMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisRiskHandleLogStore implements RiskHandleLogStore {

    private final RiskHandleLogMapper mapper;

    public MybatisRiskHandleLogStore(RiskHandleLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(RiskHandleLogEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public List<RiskHandleLogEntity> listAll() {
        return mapper.selectList(new QueryWrapper<RiskHandleLogEntity>().orderByAsc("id"));
    }
}
