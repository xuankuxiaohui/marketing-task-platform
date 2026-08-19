package com.mkt.risk.application;

import com.mkt.contract.RiskListType;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.risk.command.RiskListItemCreateCommand;
import com.mkt.risk.command.RiskListItemImportCommand;
import com.mkt.risk.convert.RiskListItemConvert;
import com.mkt.risk.convert.RiskTime;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.entity.RiskListItemEntity;
import com.mkt.risk.query.RiskListItemQuery;
import com.mkt.risk.response.RiskListImportResponse;
import com.mkt.risk.response.RiskListItemResponse;
import com.mkt.risk.support.RiskListImportParser;
import com.mkt.risk.support.RiskListProjection;
import com.mkt.risk.support.RiskOperator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskListAppService {

    private final RiskListItemStore listItemStore;
    private final RiskListProjection projection;
    private final RiskAuditAppender auditAppender;
    private final Clock clock;

    public RiskListAppService(
            RiskListItemStore listItemStore,
            RiskListProjection projection,
            RiskAuditAppender auditAppender,
            Clock clock) {
        this.listItemStore = listItemStore;
        this.projection = projection;
        this.auditAppender = auditAppender;
        this.clock = clock;
    }

    public RiskListItemResponse get(long id) {
        RiskListItemEntity existing = listItemStore.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return RiskListItemConvert.toResponse(existing);
    }

    public PageData<RiskListItemResponse> page(RiskListItemQuery query) {
        String dimension = query.dimension() == null ? null : query.dimension().name();
        String listType = query.listType() == null ? null : query.listType().name();
        LocalDateTime from = RiskTime.toUtc(query.from());
        LocalDateTime to = RiskTime.toUtc(query.to());
        long total = listItemStore.countByQuery(dimension, listType, query.value(), from, to);
        List<RiskListItemEntity> rows = listItemStore.listByQuery(
                dimension,
                listType,
                query.value(),
                from,
                to,
                query.page().offset(),
                query.page().pageSize());
        return new PageData<>(total, rows.stream().map(RiskListItemConvert::toResponse).toList());
    }

    @Transactional
    public RiskListAddResult add(RiskListItemCreateCommand command) {
        String listValue = requireNormalized(command.dimension(), command.listValue());
        requireFutureExpire(command.expireAt());
        boolean denyLogin = Boolean.TRUE.equals(command.denyLogin());
        if (denyLogin && !(command.dimension() == RiskDimension.USER && command.listType() == RiskListType.BLACK)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "denyLogin 仅可用于用户黑名单");
        }
        RiskListItemEntity existing =
                listItemStore.getByUk(command.dimension().name(), command.listType().name(), listValue);
        if (existing != null) {
            return new RiskListAddResult(RiskListItemConvert.toResponse(existing), true);
        }
        RiskListItemEntity entity = newItem(
                command.dimension(),
                command.listType(),
                listValue,
                command.reason(),
                denyLogin,
                command.expireAt() == null ? null : RiskTime.toUtc(command.expireAt()),
                command.remark());
        try {
            listItemStore.insert(entity);
        } catch (DuplicateKeyException ex) {
            RiskListItemEntity raced =
                    listItemStore.getByUk(command.dimension().name(), command.listType().name(), listValue);
            if (raced == null) {
                throw ex;
            }
            return new RiskListAddResult(RiskListItemConvert.toResponse(raced), true);
        }
        projection.scheduleReconcile(command.dimension(), command.listType(), listValue);
        auditAppender.append(
                "list-add",
                "risk_list_item",
                String.valueOf(entity.getId()),
                command.dimension() + ":" + command.listType() + ":" + listValue);
        return new RiskListAddResult(RiskListItemConvert.toResponse(entity), false);
    }

    @Transactional
    public RiskListImportResponse importItems(RiskListItemImportCommand command) {
        if (command.listType() != RiskListType.BLACK) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "白名单不支持批量导入");
        }
        RiskListImportParser.ParseResult parsed =
                RiskListImportParser.parse(command.dimension(), command.content());
        Set<String> unique = new LinkedHashSet<>(parsed.values());
        int imported = 0;
        List<String> changed = new ArrayList<>();
        for (String value : unique) {
            RiskListItemEntity existing =
                    listItemStore.getByUk(command.dimension().name(), command.listType().name(), value);
            if (existing != null) {
                imported++;
                continue;
            }
            RiskListItemEntity entity = newItem(
                    command.dimension(), command.listType(), value, command.reason(), false, null, null);
            try {
                listItemStore.insert(entity);
                imported++;
                changed.add(value);
            } catch (DuplicateKeyException ex) {
                imported++;
            }
        }
        for (String value : changed) {
            projection.scheduleReconcile(command.dimension(), command.listType(), value);
        }
        auditAppender.append(
                "list-import",
                "risk_list_item",
                command.dimension() + ":" + command.listType(),
                "imported=" + imported + ",invalid=" + parsed.invalid());
        return new RiskListImportResponse(imported, parsed.invalid());
    }

    @Transactional
    public void remove(long id, String reason) {
        RiskListItemEntity existing = listItemStore.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        listItemStore.deleteById(id);
        RiskDimension dimension = RiskDimension.valueOf(existing.getDimension());
        RiskListType listType = RiskListType.valueOf(existing.getListType());
        projection.scheduleReconcile(dimension, listType, existing.getListValue());
        auditAppender.append(
                "list-remove",
                "risk_list_item",
                String.valueOf(id),
                existing.getDimension() + ":" + existing.getListType() + ":" + existing.getListValue()
                        + ",reason=" + reason);
    }

    private RiskListItemEntity newItem(
            RiskDimension dimension,
            RiskListType listType,
            String listValue,
            String reason,
            boolean denyLogin,
            LocalDateTime expireAt,
            String remark) {
        LocalDateTime now = RiskTime.toUtc(clock.instant());
        RiskListItemEntity entity = new RiskListItemEntity();
        entity.setDimension(dimension.name());
        entity.setListType(listType.name());
        entity.setListValue(listValue);
        entity.setReason(reason);
        entity.setDenyLogin(denyLogin ? 1 : 0);
        entity.setEffectiveAt(now);
        entity.setExpireAt(expireAt);
        entity.setOperatorId(RiskOperator.requireUserId());
        entity.setRemark(remark);
        entity.setCreatedAt(now);
        return entity;
    }

    private void requireFutureExpire(Instant expireAt) {
        if (expireAt != null && !expireAt.isAfter(clock.instant())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "expireAt 必须晚于当前时间");
        }
    }

    private static String requireNormalized(RiskDimension dimension, String value) {
        String normalized = RiskListImportParser.normalizeOrNull(dimension, value);
        if (normalized == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "listValue 格式不合法");
        }
        return normalized;
    }
}
