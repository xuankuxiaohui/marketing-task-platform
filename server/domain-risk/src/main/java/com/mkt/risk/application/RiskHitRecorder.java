package com.mkt.risk.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.risk.convert.RiskTime;
import com.mkt.risk.entity.RiskHitLogEntity;
import com.mkt.risk.support.RiskFallbackProbe;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** REQUIRES_NEW hit persist + {@code risk.hit.recorded} (design §5.9). Failure → alert, not block. */
@Component
public class RiskHitRecorder {

    private final RiskHitLogStore hitLogStore;
    private final EventPublisher eventPublisher;
    private final Clock clock;
    private final RiskFallbackProbe probe;
    private final TransactionTemplate requiresNew;

    public RiskHitRecorder(
            RiskHitLogStore hitLogStore,
            EventPublisher eventPublisher,
            Clock clock,
            RiskFallbackProbe probe,
            @Nullable PlatformTransactionManager transactionManager) {
        this.hitLogStore = hitLogStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.probe = probe;
        if (transactionManager == null) {
            this.requiresNew = null;
        } else {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.requiresNew = template;
        }
    }

    public void record(
            String hitType,
            String ruleCode,
            Long userId,
            String dimensionValue,
            Map<String, Object> context,
            String hitValue,
            String threshold,
            String actionResult) {
        try {
            if (requiresNew != null) {
                requiresNew.executeWithoutResult(status -> persist(
                        hitType, ruleCode, userId, dimensionValue, context, hitValue, threshold, actionResult));
            } else {
                persist(hitType, ruleCode, userId, dimensionValue, context, hitValue, threshold, actionResult);
            }
        } catch (RuntimeException ex) {
            probe.onHitWriteFailure(ex);
        }
    }

    private void persist(
            String hitType,
            String ruleCode,
            Long userId,
            String dimensionValue,
            Map<String, Object> context,
            String hitValue,
            String threshold,
            String actionResult) {
        Instant now = clock.instant();
        RiskHitLogEntity entity = new RiskHitLogEntity();
        entity.setHitType(hitType);
        entity.setRuleCode(ruleCode);
        entity.setUserId(userId);
        entity.setDimensionValue(dimensionValue);
        entity.setContext(JsonUtil.toJson(context == null ? Map.of() : context));
        entity.setHitValue(hitValue);
        entity.setThreshold(threshold);
        entity.setActionResult(actionResult);
        entity.setSimulated(0);
        entity.setOccurredAt(RiskTime.toUtc(now));
        entity.setCreatedAt(RiskTime.toUtc(now));
        hitLogStore.insert(entity);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hitId", entity.getId());
        payload.put("scene", context == null ? null : context.get("scene"));
        payload.put("source", ruleCode);
        payload.put("action", actionResult);
        eventPublisher.append(EventCodes.RISK_HIT_RECORDED, "risk_hit_log", String.valueOf(entity.getId()), payload);
    }
}
