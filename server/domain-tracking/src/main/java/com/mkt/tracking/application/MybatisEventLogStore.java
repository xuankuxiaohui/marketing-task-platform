package com.mkt.tracking.application;

import com.mkt.tracking.entity.EvtEventLogEntity;
import com.mkt.tracking.mapper.EvtEventLogMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisEventLogStore implements EventLogStore {

    private final EvtEventLogMapper mapper;

    public MybatisEventLogStore(EvtEventLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(EvtEventLogEntity entity) {
        return mapper.insert(entity);
    }
}
