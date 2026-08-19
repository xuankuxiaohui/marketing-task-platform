package com.mkt.kernel.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.Result;
import com.mkt.kernel.trace.TraceIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.StringHttpMessageConverter;

class ResultTraceAdviceTest {

    private final ResultTraceAdvice advice = new ResultTraceAdvice();

    @AfterEach
    void clear() {
        TraceIds.clear();
    }

    @Test
    void fillsBlankTraceIdFromMdc() throws Exception {
        TraceIds.put("from-mdc");
        MethodParameter parameter = new MethodParameter(Result.class.getDeclaredMethod("ok"), -1);
        Result<String> written = (Result<String>)
                advice.beforeBodyWrite(new Result<>(0, "ok", "x", null), parameter, null, null, null, null);

        assertThat(advice.supports(parameter, StringHttpMessageConverter.class)).isTrue();
        assertThat(written.traceId()).isEqualTo("from-mdc");
        assertThat(advice.beforeBodyWrite("plain", parameter, null, null, null, null)).isEqualTo("plain");
    }
}
