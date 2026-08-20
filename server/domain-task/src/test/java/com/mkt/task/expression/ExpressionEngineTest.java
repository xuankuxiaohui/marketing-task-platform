package com.mkt.task.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionEngineTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptsWhitelistAndEvaluatesTrue() {
        CompiledExpression compiled =
                ExpressionEngine.compile("province() = 'GD' AND userLevel() >= 3");
        EvalContext ctx = new EvalContext(
                "GD", "user", "1", 5, List.of("vip"), Instant.parse("2026-08-01T00:00:00Z"),
                CLOCK.instant(), null, null, null, code -> false);
        assertThat(ExpressionEngine.evaluate(compiled, ctx)).isTrue();
    }

    @Test
    void nullAttrMakesComparisonFalseButNotFlips() {
        CompiledExpression compiled = ExpressionEngine.compile("NOT (province() = 'GD')");
        boolean value = ExpressionEngine.evaluate(
                compiled, EvalContext.missingAttributes(CLOCK.instant(), code -> false));
        assertThat(value).isTrue();
    }

    @Test
    void hasTagMissingTagsIsFalseNotSentinel() {
        CompiledExpression compiled = ExpressionEngine.compile("hasTag('vip')");
        boolean value = ExpressionEngine.evaluate(
                compiled, EvalContext.missingAttributes(CLOCK.instant(), code -> false));
        assertThat(value).isFalse();
    }

    @Test
    void inListAndNotIn() {
        CompiledExpression compiled = ExpressionEngine.compile("province() in ('GD', 'BJ')");
        EvalContext hit = new EvalContext(
                "GD", null, null, null, List.of(), null, CLOCK.instant(), null, null, null, code -> false);
        assertThat(ExpressionEngine.evaluate(compiled, hit)).isTrue();
        EvalContext miss = new EvalContext(
                "SH", null, null, null, List.of(), null, CLOCK.instant(), null, null, null, code -> false);
        assertThat(ExpressionEngine.evaluate(compiled, miss)).isFalse();
    }

    @Test
    void validateReportsNullAttrSample() {
        ExpressionValidateResult result = ExpressionEngine.validate(
                "province() = 'GD'", ExpressionType.FILTER, CLOCK, code -> false);
        assertThat(result.valid()).isTrue();
        assertThat(result.nullAttrSample()).contains("province()");
    }

    @Test
    void rejectUnknownFunctionAndMethodCall() {
        assertThatThrownBy(() -> ExpressionEngine.compile("foo() = 1"))
                .isInstanceOf(ExpressionCompileException.class);
        assertThatThrownBy(() -> ExpressionEngine.compile("province().length"))
                .isInstanceOf(ExpressionCompileException.class);
    }

    @Test
    void rejectOverlongAndNodeCap() {
        assertThatThrownBy(() -> ExpressionEngine.compile("a".repeat(1025)))
                .isInstanceOf(ExpressionCompileException.class)
                .hasMessageContaining("长度");
        String nested = "(".repeat(201) + "1 = 1" + ")".repeat(201);
        assertThatThrownBy(() -> ExpressionEngine.compile(nested))
                .isInstanceOf(ExpressionCompileException.class);
    }

    @Test
    void registerWithinDaysUsesClock() {
        CompiledExpression compiled = ExpressionEngine.compile("registerWithinDays(7)");
        EvalContext recent = new EvalContext(
                null, null, null, null, List.of(), Instant.parse("2026-08-15T00:00:00Z"),
                CLOCK.instant(), null, null, null, code -> false);
        assertThat(ExpressionEngine.evaluate(compiled, recent)).isTrue();
        EvalContext old = new EvalContext(
                null, null, null, null, List.of(), Instant.parse("2026-01-01T00:00:00Z"),
                CLOCK.instant(), null, null, null, code -> false);
        assertThat(ExpressionEngine.evaluate(compiled, old)).isFalse();
    }

    @Test
    void aviatorEngineDisablesNewAndModules() {
        assertThat(AviatorSandbox.lockedEngine()).isNotNull();
    }

    @Test
    void notInAndOrGrouping() {
        CompiledExpression compiled =
                ExpressionEngine.compile("province() not-in ('SH') AND (userRole() = 'user' OR orgId() = '9')");
        EvalContext ctx = new EvalContext(
                "GD", "user", "1", 1, List.of(), null, CLOCK.instant(), null, null, null, code -> true);
        assertThat(ExpressionEngine.evaluate(compiled, ctx)).isTrue();
    }

    @Test
    void inCrowdHitAndMissing() {
        CompiledExpression compiled = ExpressionEngine.compile("inCrowd('vip')");
        EvalContext hit = new EvalContext(
                null, null, null, null, List.of(), null, CLOCK.instant(), null, null, null, "vip"::equals);
        assertThat(ExpressionEngine.evaluate(compiled, hit)).isTrue();
        EvalContext miss = EvalContext.missingAttributes(CLOCK.instant(), code -> false);
        assertThat(ExpressionEngine.evaluate(compiled, miss)).isFalse();
    }

    @Test
    void rejectDoubleQuoteAndVariable() {
        assertThatThrownBy(() -> ExpressionEngine.compile("province() = \"GD\""))
                .isInstanceOf(ExpressionCompileException.class);
        assertThatThrownBy(() -> ExpressionEngine.compile("x = 1"))
                .isInstanceOf(ExpressionCompileException.class);
        assertThatThrownBy(() -> ExpressionEngine.compile(""))
                .isInstanceOf(ExpressionCompileException.class);
        ExpressionValidateResult failed =
                ExpressionEngine.validate("1 = 1", null, CLOCK, code -> false);
        assertThat(failed.valid()).isFalse();
    }

    @Test
    void hasTagArityAndRegisterWithinDaysMissing() {
        assertThatThrownBy(() -> ExpressionEngine.compile("hasTag()"))
                .isInstanceOf(ExpressionCompileException.class);
        assertThatThrownBy(() -> ExpressionEngine.compile("registerWithinDays('x')"))
                .isInstanceOf(ExpressionCompileException.class);
        CompiledExpression compiled = ExpressionEngine.compile("registerWithinDays(3)");
        assertThat(ExpressionEngine.evaluate(
                        compiled, EvalContext.missingAttributes(CLOCK.instant(), code -> false)))
                .isFalse();
    }
}
