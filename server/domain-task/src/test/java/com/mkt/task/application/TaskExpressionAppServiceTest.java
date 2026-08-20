package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.command.ExpressionValidateCommand;
import com.mkt.task.testsupport.MemoryTaskCrowdStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TaskExpressionAppServiceTest {

    @Test
    void validateAcceptsAndReportsNullSample() {
        TaskExpressionAppService service = new TaskExpressionAppService(
                new MemoryTaskCrowdStore(),
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
        var result = service.validate(new ExpressionValidateCommand("province() = 'GD'", "FILTER"));
        assertThat(result.valid()).isTrue();
        assertThat(result.nullAttrSample()).contains("province()");
    }

    @Test
    void validateRejectsUnknownFunction() {
        TaskExpressionAppService service = new TaskExpressionAppService(
                new MemoryTaskCrowdStore(),
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
        var result = service.validate(new ExpressionValidateCommand("sysDate() > 1", "BRANCH"));
        assertThat(result.valid()).isFalse();
        assertThat(result.error()).isNotNull();
    }
}
