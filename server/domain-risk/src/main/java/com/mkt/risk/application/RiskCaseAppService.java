package com.mkt.risk.application;

import com.mkt.contract.RiskListType;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.risk.command.RiskCaseHandleCommand;
import com.mkt.risk.convert.RiskHitLogConvert;
import com.mkt.risk.convert.RiskTime;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskHandleAction;
import com.mkt.risk.entity.RiskHandleLogEntity;
import com.mkt.risk.entity.RiskHitLogEntity;
import com.mkt.risk.entity.RiskListItemEntity;
import com.mkt.risk.query.RiskHitQuery;
import com.mkt.risk.response.RiskHitLogResponse;
import com.mkt.risk.support.RiskListProjection;
import com.mkt.risk.support.RiskOperator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskCaseAppService {

    private final RiskHitLogStore hitLogStore;
    private final RiskHandleLogStore handleLogStore;
    private final RiskListItemStore listItemStore;
    private final RiskListProjection projection;
    private final RiskAuditAppender auditAppender;
    private final Clock clock;

    public RiskCaseAppService(
            RiskHitLogStore hitLogStore,
            RiskHandleLogStore handleLogStore,
            RiskListItemStore listItemStore,
            RiskListProjection projection,
            RiskAuditAppender auditAppender,
            Clock clock) {
        this.hitLogStore = hitLogStore;
        this.handleLogStore = handleLogStore;
        this.listItemStore = listItemStore;
        this.projection = projection;
        this.auditAppender = auditAppender;
        this.clock = clock;
    }

    public PageData<RiskHitLogResponse> pageHits(RiskHitQuery query) {
        LocalDateTime from = RiskTime.toUtc(query.from());
        LocalDateTime to = RiskTime.toUtc(query.to());
        long total = hitLogStore.countByQuery(
                query.ruleCode(),
                query.hitType(),
                query.dimensionValue(),
                query.userId(),
                query.actionResult(),
                from,
                to);
        List<RiskHitLogEntity> rows = hitLogStore.listByQuery(
                query.ruleCode(),
                query.hitType(),
                query.dimensionValue(),
                query.userId(),
                query.actionResult(),
                from,
                to,
                query.page().offset(),
                query.page().pageSize());
        return new PageData<>(total, rows.stream().map(RiskHitLogConvert::toResponse).toList());
    }

    @Transactional
    public void handle(RiskCaseHandleCommand command) {
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "reason 必填");
        }
        Long userId = resolveUserId(command);
        boolean toWhitelist = Boolean.TRUE.equals(command.toWhitelist());
        switch (command.action()) {
            case ADD_BLACK -> addBlack(userId, command);
            case REMOVE_BLACK -> removeBlack(userId, toWhitelist, command);
            case MARK_FALSE_POSITIVE -> {
                // list unchanged
            }
        }
        RiskHandleLogEntity log = new RiskHandleLogEntity();
        log.setHitLogId(command.hitLogId());
        log.setUserId(userId);
        log.setAction(command.action().name());
        log.setToWhitelist(toWhitelist ? 1 : 0);
        log.setOperatorId(RiskOperator.requireUserId());
        log.setReason(command.reason());
        log.setCreatedAt(RiskTime.toUtc(clock.instant()));
        handleLogStore.insert(log);
        auditAppender.append(
                "case-handle",
                "risk_handle_log",
                String.valueOf(log.getId()),
                command.action() + ",userId=" + userId + ",toWhitelist=" + toWhitelist);
    }

    private Long resolveUserId(RiskCaseHandleCommand command) {
        if (command.userId() != null) {
            return command.userId();
        }
        if (command.hitLogId() == null) {
            if (command.action() == RiskHandleAction.MARK_FALSE_POSITIVE) {
                return null;
            }
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "userId 或 hitLogId 必填");
        }
        RiskHitLogEntity hit = hitLogStore.getById(command.hitLogId());
        if (hit == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return hit.getUserId();
    }

    private void addBlack(Long userId, RiskCaseHandleCommand command) {
        String value = requireUserValue(userId, "加黑需要 userId");
        requireFutureExpire(command.expireAt());
        RiskListItemEntity existing =
                listItemStore.getByUk(RiskDimension.USER.name(), RiskListType.BLACK.name(), value);
        if (existing != null) {
            return;
        }
        LocalDateTime now = RiskTime.toUtc(clock.instant());
        RiskListItemEntity entity = new RiskListItemEntity();
        entity.setDimension(RiskDimension.USER.name());
        entity.setListType(RiskListType.BLACK.name());
        entity.setListValue(value);
        entity.setReason(command.reason());
        entity.setDenyLogin(0);
        entity.setEffectiveAt(now);
        entity.setExpireAt(RiskTime.toUtc(command.expireAt()));
        entity.setOperatorId(RiskOperator.requireUserId());
        entity.setCreatedAt(now);
        try {
            listItemStore.insert(entity);
        } catch (DuplicateKeyException ignored) {
            return;
        }
        projection.scheduleReconcile(RiskDimension.USER, RiskListType.BLACK, value);
    }

    private void removeBlack(Long userId, boolean toWhitelist, RiskCaseHandleCommand command) {
        String value = requireUserValue(userId, "解黑需要 userId");
        RiskListItemEntity black =
                listItemStore.getByUk(RiskDimension.USER.name(), RiskListType.BLACK.name(), value);
        if (black == null) {
            if (toWhitelist) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "解除黑名单后才能移入白名单");
            }
            return;
        }
        listItemStore.deleteById(black.getId());
        projection.scheduleReconcile(RiskDimension.USER, RiskListType.BLACK, value);
        if (!toWhitelist) {
            return;
        }
        RiskListItemEntity white =
                listItemStore.getByUk(RiskDimension.USER.name(), RiskListType.WHITE.name(), value);
        if (white != null) {
            return;
        }
        LocalDateTime now = RiskTime.toUtc(clock.instant());
        RiskListItemEntity entity = new RiskListItemEntity();
        entity.setDimension(RiskDimension.USER.name());
        entity.setListType(RiskListType.WHITE.name());
        entity.setListValue(value);
        entity.setReason(command.reason());
        entity.setDenyLogin(0);
        entity.setEffectiveAt(now);
        entity.setOperatorId(RiskOperator.requireUserId());
        entity.setCreatedAt(now);
        try {
            listItemStore.insert(entity);
        } catch (DuplicateKeyException ignored) {
            return;
        }
        projection.scheduleReconcile(RiskDimension.USER, RiskListType.WHITE, value);
    }

    private void requireFutureExpire(Instant expireAt) {
        if (expireAt != null && !expireAt.isAfter(clock.instant())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "expireAt 必须晚于当前时间");
        }
    }

    private static String requireUserValue(Long userId, String missingMessage) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, missingMessage);
        }
        if (userId <= 0L) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "userId 必须为正整数");
        }
        return Long.toString(userId);
    }
}
