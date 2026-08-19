package com.mkt.tracking.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.tracking.command.TrackMetadataSaveCommand;
import com.mkt.tracking.convert.TrackMetadataConvert;
import com.mkt.tracking.domain.EventCodeSyntax;
import com.mkt.tracking.domain.MetadataStatuses;
import com.mkt.tracking.domain.PropSchemaItem;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import com.mkt.tracking.query.TrackMetadataQuery;
import com.mkt.tracking.response.TrackMetadataResponse;
import com.mkt.tracking.support.TrackErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackMetadataAppService {

    private final EventMetadataStore store;
    private final TrackAuditAppender auditAppender;
    private final Clock clock;

    public TrackMetadataAppService(EventMetadataStore store, TrackAuditAppender auditAppender, Clock clock) {
        this.store = store;
        this.auditAppender = auditAppender;
        this.clock = clock;
    }

    public TrackMetadataResponse get(long id) {
        return TrackMetadataConvert.toResponse(require(id));
    }

    public PageData<TrackMetadataResponse> page(TrackMetadataQuery query) {
        String status = normalizeFilterStatus(query.status());
        String eventCode = blankToNull(query.eventCode());
        long total = store.countByQuery(eventCode, status);
        List<EvtEventMetadataEntity> rows =
                store.listByQuery(eventCode, status, query.page().offset(), query.page().pageSize());
        return new PageData<>(total, rows.stream().map(TrackMetadataConvert::toResponse).toList());
    }

    @Transactional
    public TrackMetadataResponse create(TrackMetadataSaveCommand command) {
        TrackMetadataSaveCommand normalized = requireValid(command);
        EvtEventMetadataEntity existing = store.getByEventCode(normalized.eventCode());
        if (existing != null) {
            throw new BusinessException(TrackErrorCodes.METADATA_DUPLICATE_CODE);
        }
        EvtEventMetadataEntity entity = TrackMetadataConvert.toEntity(normalized, clock);
        try {
            store.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(TrackErrorCodes.METADATA_DUPLICATE_CODE, ex);
        }
        auditAppender.append(
                "metadata-create",
                "evt_event_metadata",
                String.valueOf(entity.getId()),
                normalized.eventCode() + ":" + normalized.status());
        return TrackMetadataConvert.toResponse(entity);
    }

    @Transactional
    public TrackMetadataResponse update(long id, TrackMetadataSaveCommand command) {
        EvtEventMetadataEntity existing = require(id);
        TrackMetadataSaveCommand normalized = requireValid(command);
        if (!existing.getEventCode().equals(normalized.eventCode())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "eventCode 不可修改");
        }
        TrackMetadataConvert.apply(normalized, existing);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        store.update(existing);
        auditAppender.append(
                "metadata-update",
                "evt_event_metadata",
                String.valueOf(id),
                normalized.eventCode() + ":" + normalized.status());
        return TrackMetadataConvert.toResponse(existing);
    }

    @Transactional
    public void delete(long id) {
        EvtEventMetadataEntity existing = require(id);
        store.deleteById(id);
        auditAppender.append(
                "metadata-delete", "evt_event_metadata", String.valueOf(id), existing.getEventCode());
    }

    private EvtEventMetadataEntity require(long id) {
        EvtEventMetadataEntity existing = store.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private static TrackMetadataSaveCommand requireValid(TrackMetadataSaveCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        String eventCode = command.eventCode() == null ? "" : command.eventCode().trim();
        if (!EventCodeSyntax.valid(eventCode)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "eventCode 格式非法");
        }
        String status = command.status() == null ? "" : command.status().trim().toUpperCase(Locale.ROOT);
        if (!MetadataStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "status 仅允许 ENABLED|DISABLED");
        }
        validateSchema(command.propSchema());
        return new TrackMetadataSaveCommand(
                eventCode,
                command.name().trim(),
                command.propSchema(),
                status,
                command.owner(),
                command.remark());
    }

    private static void validateSchema(List<PropSchemaItem> schema) {
        if (schema == null) {
            return;
        }
        for (PropSchemaItem item : schema) {
            if (item == null
                    || item.name() == null
                    || item.name().isBlank()
                    || item.name().length() > 64
                    || item.type() == null
                    || item.type().isBlank()
                    || item.type().length() > 32) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "propSchema 非法");
            }
            if (item.remark() != null && item.remark().length() > 255) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "propSchema.remark 超长");
            }
        }
    }

    private static String normalizeFilterStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!MetadataStatuses.valid(normalized)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "status 仅允许 ENABLED|DISABLED");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
