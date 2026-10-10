package com.mkt.tracking.application;

import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.domain.MetadataStatuses;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import com.mkt.tracking.mapper.EvtEventMetadataMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisEventMetadataStore implements EventMetadataStore {

    private final EvtEventMetadataMapper mapper;

    public MybatisEventMetadataStore(EvtEventMetadataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MetadataStatus statusOf(String eventCode) {
        if (eventCode == null) {
            return MetadataStatus.MISSING;
        }
        return statusesOf(List.of(eventCode)).getOrDefault(eventCode, MetadataStatus.MISSING);
    }

    @Override
    public Map<String, MetadataStatus> statusesOf(Collection<String> eventCodes) {
        if (eventCodes == null || eventCodes.isEmpty()) {
            return Map.of();
        }
        List<String> codes = new ArrayList<>();
        for (String code : eventCodes) {
            if (code != null) {
                codes.add(code);
            }
        }
        if (codes.isEmpty()) {
            return Map.of();
        }
        List<EvtEventMetadataEntity> rows = mapper.selectByEventCodes(codes);
        Map<String, MetadataStatus> found = new HashMap<>();
        if (rows != null) {
            for (EvtEventMetadataEntity row : rows) {
                if (row != null && row.getEventCode() != null) {
                    found.put(row.getEventCode(), MetadataStatuses.of(row.getStatus()));
                }
            }
        }
        Map<String, MetadataStatus> result = new LinkedHashMap<>();
        for (String code : eventCodes) {
            if (code == null) {
                continue;
            }
            result.put(code, found.getOrDefault(code, MetadataStatus.MISSING));
        }
        return result;
    }

    @Override
    public EvtEventMetadataEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public EvtEventMetadataEntity getByEventCode(String eventCode) {
        return mapper.selectByEventCode(eventCode);
    }

    @Override
    public int insert(EvtEventMetadataEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(EvtEventMetadataEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int deleteById(long id) {
        return mapper.deleteById(id);
    }

    @Override
    public long countByQuery(String eventCode, String status) {
        return mapper.selectCountByQuery(eventCode, status);
    }

    @Override
    public List<EvtEventMetadataEntity> listByQuery(String eventCode, String status, long offset, int limit) {
        return mapper.selectByQuery(eventCode, status, offset, limit);
    }
}
