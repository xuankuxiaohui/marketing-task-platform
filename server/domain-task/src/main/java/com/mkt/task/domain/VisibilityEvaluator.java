package com.mkt.task.domain;

import com.mkt.contract.UserAttributes;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.expression.CompiledExpression;
import com.mkt.task.expression.EvalContext;
import com.mkt.task.expression.ExpressionCompileException;
import com.mkt.task.expression.ExpressionEngine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Pure visibility: PUBLISHED ∧ window ∧ grayHit ∧ filterPass (design §5.3). */
public final class VisibilityEvaluator {

    private VisibilityEvaluator() {}

    /**
     * List hot-path: boolean only, fail-fast, no ArrayList / VisibilityResult alloc per task.
     * Same allow predicate as {@link #evaluate} without reason collection.
     */
    public static boolean visible(
            String status,
            Instant startTime,
            Instant endTime,
            TaskGrayCommand gray,
            TaskFilterCommand filter,
            long taskId,
            long userId,
            Instant now,
            CrowdMembership crowds,
            EvalContext evalContext) {
        return DefinitionStatuses.PUBLISHED.equals(status)
                && inWindow(startTime, endTime, now)
                && grayHit(gray, taskId, userId, crowds)
                && filterPass(filter, userId, crowds, evalContext);
    }

    public static VisibilityResult evaluate(
            String status,
            Instant startTime,
            Instant endTime,
            TaskGrayCommand gray,
            TaskFilterCommand filter,
            long taskId,
            long userId,
            UserAttributes attrs,
            Instant now,
            CrowdMembership crowds,
            EvalContext evalContext) {
        // Allocate reasons only on deny — allow reuses VisibilityResult.ALLOW.
        VisibilityReason r0 = null;
        VisibilityReason r1 = null;
        VisibilityReason r2 = null;
        VisibilityReason r3 = null;
        int n = 0;
        if (!DefinitionStatuses.PUBLISHED.equals(status)) {
            r0 = VisibilityReason.OFFLINE;
            n++;
        }
        if (!inWindow(startTime, endTime, now)) {
            r1 = VisibilityReason.NOT_IN_WINDOW;
            n++;
        }
        if (!grayHit(gray, taskId, userId, crowds)) {
            r2 = VisibilityReason.GRAY_MISS;
            n++;
        }
        if (!filterPass(filter, userId, crowds, evalContext)) {
            r3 = VisibilityReason.FILTER_MISS;
            n++;
        }
        if (n == 0) {
            return VisibilityResult.allow();
        }
        List<VisibilityReason> reasons = new ArrayList<>(n);
        if (r0 != null) {
            reasons.add(r0);
        }
        if (r1 != null) {
            reasons.add(r1);
        }
        if (r2 != null) {
            reasons.add(r2);
        }
        if (r3 != null) {
            reasons.add(r3);
        }
        return VisibilityResult.deny(reasons);
    }

    /** Published + window + no gray/filter that needs a user. Guests must not see personalized cards. */
    public static boolean publicCard(
            String status,
            Instant startTime,
            Instant endTime,
            TaskGrayCommand gray,
            TaskFilterCommand filter,
            Instant now) {
        if (!DefinitionStatuses.PUBLISHED.equals(status) || !inWindow(startTime, endTime, now)) {
            return false;
        }
        return !needsIdentity(gray, filter);
    }

    public static boolean needsIdentity(TaskGrayCommand gray, TaskFilterCommand filter) {
        if (gray != null && gray.type() != null && !GrayTypes.NONE.equals(gray.type())) {
            return true;
        }
        if (filter == null) {
            return false;
        }
        if (filter.expr() != null && !filter.expr().isBlank()) {
            return true;
        }
        return notEmpty(filter.allowCrowdIds()) || notEmpty(filter.excludeCrowdIds());
    }

    private static boolean notEmpty(List<Long> ids) {
        return ids != null && !ids.isEmpty();
    }

    public static boolean inWindow(Instant startTime, Instant endTime, Instant now) {
        if (now == null) {
            return false;
        }
        if (startTime != null && now.isBefore(startTime)) {
            return false;
        }
        if (endTime != null && !now.isBefore(endTime)) {
            return false;
        }
        return true;
    }

    public static boolean grayHit(TaskGrayCommand gray, long taskId, long userId, CrowdMembership crowds) {
        if (gray == null || gray.type() == null || GrayTypes.NONE.equals(gray.type())) {
            return true;
        }
        int bucket = GrayBucket.of(userId, taskId);
        return switch (gray.type()) {
            case GrayTypes.RATIO -> gray.ratio() != null && bucket < gray.ratio();
            case GrayTypes.AB -> abHit(gray.abGroup(), bucket);
            case GrayTypes.CROWD -> crowdHit(gray.crowdId(), gray.excludeCrowdId(), userId, crowds);
            default -> false;
        };
    }

    public static boolean filterPass(
            TaskFilterCommand filter, long userId, CrowdMembership crowds, EvalContext evalContext) {
        if (filter == null) {
            return true;
        }
        if (filter.expr() != null && !filter.expr().isBlank()) {
            if (!evalExpr(filter.expr(), evalContext)) {
                return false;
            }
        }
        List<Long> allow = filter.allowCrowdIds();
        if (allow != null && !allow.isEmpty()) {
            boolean hit = false;
            for (Long crowdId : allow) {
                if (crowdId != null && crowds != null && crowds.contains(crowdId, userId)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }
        List<Long> exclude = filter.excludeCrowdIds();
        if (exclude != null) {
            for (Long crowdId : exclude) {
                if (crowdId != null && crowds != null && crowds.contains(crowdId, userId)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean abHit(String abGroup, int bucket) {
        String group = bucket % 2 == 0 ? "A" : "B";
        if (abGroup == null || abGroup.isBlank()) {
            return false;
        }
        return "AB".equals(abGroup) || group.equals(abGroup);
    }

    private static boolean crowdHit(Long crowdId, Long excludeCrowdId, long userId, CrowdMembership crowds) {
        if (crowdId == null || crowds == null || !crowds.contains(crowdId, userId)) {
            return false;
        }
        if (excludeCrowdId != null && crowds.contains(excludeCrowdId, userId)) {
            return false;
        }
        return true;
    }

    private static boolean evalExpr(String expr, EvalContext evalContext) {
        try {
            CompiledExpression compiled = ExpressionEngine.compileCached(expr);
            return ExpressionEngine.evaluate(compiled, evalContext);
        } catch (ExpressionCompileException ex) {
            return false;
        }
    }
}
