package com.mkt.kernel.autoconfigure;

import com.mkt.kernel.json.JsonMapperConfiguration;
import com.mkt.kernel.openapi.OpenApiGroupsConfiguration;
import com.mkt.kernel.time.ClockConfiguration;
import com.mkt.kernel.trace.TraceIdFilter;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.kernel.web.ResultTraceAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    ClockConfiguration.class,
    JsonMapperConfiguration.class,
    GlobalExceptionHandler.class,
    ResultTraceAdvice.class,
    TraceIdFilter.class,
    OpenApiGroupsConfiguration.class
})
public class KernelAutoConfiguration {
}
