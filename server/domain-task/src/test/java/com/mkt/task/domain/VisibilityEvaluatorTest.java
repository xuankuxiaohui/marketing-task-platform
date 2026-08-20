package com.mkt.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.expression.EvalContext;
import com.mkt.task.expression.EvalContexts;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VisibilityEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
    private static final CrowdMembership NONE = (crowdId, userId) -> false;

    @Test
    void publishedOpenWindowNoneGrayIsVisible() {
        UserAttributes attrs = attrs("GD", List.of("vip"));
        VisibilityResult result = VisibilityEvaluator.evaluate(
                DefinitionStatuses.PUBLISHED,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                new TaskGrayCommand(GrayTypes.NONE, null, null, null, null),
                new TaskFilterCommand(null, List.of(), List.of()),
                1L,
                1L,
                attrs,
                NOW,
                NONE,
                EvalContexts.filter(attrs, NOW, code -> false));
        assertThat(result.visible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void offlineAndOutOfWindowCollectReasons() {
        UserAttributes attrs = attrs("GD", List.of());
        VisibilityResult result = VisibilityEvaluator.evaluate(
                DefinitionStatuses.OFFLINE,
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2028-01-01T00:00:00Z"),
                new TaskGrayCommand(GrayTypes.RATIO, 0, null, null, null),
                new TaskFilterCommand("province() = 'BJ'", List.of(), List.of()),
                1L,
                1L,
                attrs,
                NOW,
                NONE,
                EvalContexts.filter(attrs, NOW, code -> false));
        assertThat(result.visible()).isFalse();
        assertThat(result.reasons())
                .contains(
                        VisibilityReason.OFFLINE,
                        VisibilityReason.NOT_IN_WINDOW,
                        VisibilityReason.GRAY_MISS,
                        VisibilityReason.FILTER_MISS);
    }

    @Test
    void crowdGrayIsAllowAndNotExclude() {
        CrowdMembership membership = (crowdId, userId) -> crowdId == 10L;
        assertThat(VisibilityEvaluator.grayHit(
                        new TaskGrayCommand(GrayTypes.CROWD, null, null, 10L, 20L), 1L, 9L, membership))
                .isTrue();
        CrowdMembership both = (crowdId, userId) -> crowdId == 10L || crowdId == 20L;
        assertThat(VisibilityEvaluator.grayHit(
                        new TaskGrayCommand(GrayTypes.CROWD, null, null, 10L, 20L), 1L, 9L, both))
                .isFalse();
    }

    @Test
    void missingAttributeMakesComparisonFalse() {
        UserAttributes attrs =
                new UserAttributes(null, null, null, null, List.of(), null, AccountStatus.ACTIVE);
        EvalContext ctx = EvalContexts.filter(attrs, NOW, code -> false);
        assertThat(VisibilityEvaluator.filterPass(
                        new TaskFilterCommand("province() = 'GD'", List.of(), List.of()), 1L, NONE, ctx))
                .isFalse();
        assertThat(VisibilityEvaluator.filterPass(
                        new TaskFilterCommand("NOT (province() = 'GD')", List.of(), List.of()), 1L, NONE, ctx))
                .isTrue();
    }

    @Test
    void filterAllowAndExcludeCrowds() {
        CrowdMembership membership = (crowdId, userId) -> Set.of(1L, 2L).contains(crowdId);
        EvalContext ctx = EvalContext.missingAttributes(NOW, code -> false);
        assertThat(VisibilityEvaluator.filterPass(
                        new TaskFilterCommand(null, List.of(1L), List.of()), 8L, membership, ctx))
                .isTrue();
        assertThat(VisibilityEvaluator.filterPass(
                        new TaskFilterCommand(null, List.of(9L), List.of()), 8L, membership, ctx))
                .isFalse();
        assertThat(VisibilityEvaluator.filterPass(
                        new TaskFilterCommand(null, List.of(), List.of(2L)), 8L, membership, ctx))
                .isFalse();
    }

    @Test
    void nullWindowIsOpen() {
        assertThat(VisibilityEvaluator.inWindow(null, null, NOW)).isTrue();
        assertThat(VisibilityEvaluator.inWindow(NOW, NOW, NOW)).isFalse();
    }

    private static UserAttributes attrs(String province, List<String> tags) {
        return new UserAttributes(
                province, "user", "1", 1, tags, Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }
}
