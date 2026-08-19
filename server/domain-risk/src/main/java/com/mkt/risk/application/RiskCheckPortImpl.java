package com.mkt.risk.application;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserRiskSummary;
import com.mkt.risk.convert.RiskTime;
import com.mkt.risk.domain.ListDecision;
import com.mkt.risk.domain.ListSegmentOutcome;
import com.mkt.risk.domain.RiskCntKeys;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.domain.RuleDecisionEngine;
import com.mkt.risk.domain.RuleHit;
import com.mkt.risk.domain.RuleOutcome;
import com.mkt.risk.domain.RuleSpec;
import com.mkt.risk.domain.WindowCounts;
import com.mkt.risk.entity.RiskListItemEntity;
import com.mkt.risk.support.ListLookup;
import com.mkt.risk.support.RiskCntWindow;
import com.mkt.risk.support.RiskFallbackPolicy;
import com.mkt.risk.support.RiskFallbackProbe;
import com.mkt.risk.support.RiskFallbackSettings;
import com.mkt.risk.support.RiskListImportParser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Design §5.9 chain. Does not throw business exceptions. */
@Service
public class RiskCheckPortImpl implements RiskCheckPort {

    private final ListLookup listLookup;
    private final RiskRuleConfigStore ruleConfigStore;
    private final RiskCntWindow window;
    private final RiskHitRecorder recorder;
    private final RiskHitLogStore hitLogStore;
    private final RiskListItemStore listItemStore;
    private final RiskFallbackSettings fallbackSettings;
    private final RiskFallbackProbe probe;
    private final Clock clock;

    public RiskCheckPortImpl(
            ListLookup listLookup,
            RiskRuleConfigStore ruleConfigStore,
            RiskCntWindow window,
            RiskHitRecorder recorder,
            RiskHitLogStore hitLogStore,
            RiskListItemStore listItemStore,
            RiskFallbackSettings fallbackSettings,
            RiskFallbackProbe probe,
            Clock clock) {
        this.listLookup = listLookup;
        this.ruleConfigStore = ruleConfigStore;
        this.window = window;
        this.recorder = recorder;
        this.hitLogStore = hitLogStore;
        this.listItemStore = listItemStore;
        this.fallbackSettings = fallbackSettings;
        this.probe = probe;
        this.clock = clock;
    }

    @Override
    public RiskVerdict check(RiskScene scene, RiskSubject subject) {
        try {
            return doCheck(scene, subject);
        } catch (RuntimeException ex) {
            RiskFallbackPolicy policy = fallbackSettings.policy();
            probe.onFallback(policy, ex);
            return new RiskVerdict(policy == RiskFallbackPolicy.REJECT ? RiskAction.REJECT : RiskAction.PASS);
        }
    }

    @Override
    public UserRiskSummary userSummary(long userId) {
        long hitCount = hitLogStore.countByQuery(null, null, null, userId, null, null, null);
        List<RiskListType> status = new ArrayList<>(2);
        Instant now = clock.instant();
        if (liveUserList(userId, RiskListType.BLACK, now)) {
            status.add(RiskListType.BLACK);
        }
        if (liveUserList(userId, RiskListType.WHITE, now)) {
            status.add(RiskListType.WHITE);
        }
        return new UserRiskSummary(hitCount, status);
    }

    private RiskVerdict doCheck(RiskScene scene, RiskSubject subject) {
        ListSegmentOutcome list = listLookup.evaluate(scene, subject);
        if (list.decision() == ListDecision.REJECT) {
            recordListHit(scene, subject, list);
            return new RiskVerdict(RiskAction.REJECT);
        }
        if (scene == RiskScene.REGISTER || scene == RiskScene.LOGIN) {
            return new RiskVerdict(RiskAction.PASS);
        }
        if (list.decision() == ListDecision.SKIP_RULES) {
            return new RiskVerdict(RiskAction.PASS);
        }
        List<RuleSpec> rules = ruleConfigStore.listAll();
        WindowCounts counts = collectCounts(scene, subject, rules);
        RuleOutcome outcome = RuleDecisionEngine.decide(scene, subject.elapsedSeconds(), counts, rules);
        for (RuleHit hit : outcome.hits()) {
            recordRuleHit(scene, subject, hit);
        }
        return new RiskVerdict(outcome.verdict());
    }

    private WindowCounts collectCounts(RiskScene scene, RiskSubject subject, List<RuleSpec> rules) {
        long now = clock.instant().toEpochMilli();
        String ip = RiskListImportParser.normalizeOrNull(RiskDimension.IP, subject.ip());
        String device = subject.deviceId() == null
                ? null
                : RiskListImportParser.normalizeOrNull(RiskDimension.DEVICE, subject.deviceId());
        Long ra = windowCount(RiskRuleCode.RA, RiskCntKeys.DIM_USER, userDim(subject), rules, now);
        Long rb = windowCount(RiskRuleCode.RB, RiskCntKeys.DIM_USER, userDim(subject), rules, now);
        Long rc = windowCount(RiskRuleCode.RC, RiskCntKeys.DIM_IP, ip, rules, now);
        Long rd = windowCount(RiskRuleCode.RD, RiskCntKeys.DIM_DEVICE, device, rules, now);
        Long rf = rfCount(scene, ip, rules, now);
        return new WindowCounts(ra, rb, rc, rd, rf);
    }

    private Long rfCount(RiskScene scene, String ip, List<RuleSpec> rules, long nowMillis) {
        if (scene != RiskScene.CLAIM && scene != RiskScene.GRANT) {
            return null;
        }
        RuleSpec spec = find(rules, RiskRuleCode.RF);
        if (spec == null || !spec.enabled() || spec.windowSeconds() == null || ip == null) {
            return null;
        }
        return window.addAndCount(
                RiskRuleCode.RF,
                RiskCntKeys.DIM_IP,
                ip,
                window.uniqueMember(nowMillis),
                spec.windowSeconds(),
                nowMillis);
    }

    private Long windowCount(
            RiskRuleCode code, String dimension, String value, List<RuleSpec> rules, long nowMillis) {
        if (value == null || value.isBlank()) {
            return null;
        }
        RuleSpec spec = find(rules, code);
        if (spec == null || !spec.enabled() || spec.windowSeconds() == null) {
            return null;
        }
        return window.count(code, dimension, value, spec.windowSeconds(), nowMillis);
    }

    private static RuleSpec find(List<RuleSpec> rules, RiskRuleCode code) {
        for (RuleSpec spec : rules) {
            if (spec.code() == code) {
                return spec;
            }
        }
        return null;
    }

    private static String userDim(RiskSubject subject) {
        return subject.userId() == null ? null : String.valueOf(subject.userId());
    }

    private void recordListHit(RiskScene scene, RiskSubject subject, ListSegmentOutcome list) {
        recorder.record(
                "LIST",
                list.ruleCode(),
                subject.userId(),
                list.dimensionValue(),
                context(scene, subject),
                "1",
                "1",
                "REJECTED");
    }

    private void recordRuleHit(RiskScene scene, RiskSubject subject, RuleHit hit) {
        recorder.record(
                "RULE",
                hit.code().code(),
                subject.userId(),
                dimensionValue(hit.code(), subject),
                context(scene, subject),
                String.valueOf(hit.hitValue()),
                String.valueOf(hit.threshold()),
                actionResult(hit.action()));
    }

    private static String dimensionValue(RiskRuleCode code, RiskSubject subject) {
        return switch (code) {
            case RA, RB, RE -> userDim(subject);
            case RC, RF -> RiskListImportParser.normalizeOrNull(RiskDimension.IP, subject.ip());
            case RD -> subject.deviceId() == null
                    ? null
                    : RiskListImportParser.normalizeOrNull(RiskDimension.DEVICE, subject.deviceId());
        };
    }

    private static Map<String, Object> context(RiskScene scene, RiskSubject subject) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("scene", scene.name());
        ctx.put("ip", subject.ip());
        ctx.put("deviceId", subject.deviceId());
        ctx.put("elapsedSeconds", subject.elapsedSeconds());
        return ctx;
    }

    private static String actionResult(RiskAction action) {
        return switch (action) {
            case REJECT -> "REJECTED";
            case SILENT_REJECT -> "SILENT_REJECTED";
            case MARK -> "MARKED";
            case PASS -> "PASSED";
        };
    }

    private boolean liveUserList(long userId, RiskListType type, Instant now) {
        String value = RiskListImportParser.normalizeOrNull(RiskDimension.USER, String.valueOf(userId));
        if (value == null) {
            return false;
        }
        RiskListItemEntity row = listItemStore.getByUk(RiskDimension.USER.name(), type.name(), value);
        if (row == null) {
            return false;
        }
        Instant expireAt = RiskTime.toInstant(row.getExpireAt());
        return expireAt == null || expireAt.isAfter(now);
    }
}
