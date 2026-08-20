package com.mkt.task.expression;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvalContextsTest {

    @Test
    void nullAttrsUseMissingSentinels() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        EvalContext filter = EvalContexts.filter(null, now, code -> false);
        assertThat(filter.province()).isNull();
        EvalContext branch = EvalContexts.branch(null, now, code -> false, "s1", 1, 3);
        assertThat(branch.stepCode()).isEqualTo("s1");
        assertThat(branch.progressCurrent()).isEqualTo(1);
        UserAttributes attrs =
                new UserAttributes("GD", "u", "1", 2, List.of("t"), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
        EvalContext filled = EvalContexts.branch(attrs, now, code -> true, "s2", 0, 5);
        assertThat(filled.province()).isEqualTo("GD");
        assertThat(filled.stepCode()).isEqualTo("s2");
    }
}
