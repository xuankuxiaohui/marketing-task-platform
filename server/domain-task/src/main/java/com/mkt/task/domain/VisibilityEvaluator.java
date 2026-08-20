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
        List<VisibilityReason> reasons = new ArrayList<>(4);
        if (!DefinitionStatuses.PUBLISHED.equals(status)) {
            reasons.add(VisibilityReason.OFFLINE);
        }
        if (!inWindow(startTime, endTime, now)) {
            reasons.add(VisibilityReason.NOT_IN_WINDOW);
        }
        if (!grayHit(gray, taskId, userId, crowds)) {
            reasons.add(VisibilityReason.GRAY_MISS);
        }
        if (!filterPass(filter, userId, crowds, evalContext)) {
            reasons.add(VisibilityReason.FILTER_MISS);
        }
        if (reasons.isEmpty()) {
            return VisibilityResult.allow();
        }
        return VisibilityResult.deny(reasons);
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
            CompiledExpression compiled = ExpressionEngine.compile(expr);
            return ExpressionEngine.evaluate(compiled, evalContext);
        } catch (ExpressionCompileException ex) {
            return false;
        }
    }
}
