package com.mkt.kernel.web;

import com.mkt.kernel.Result;
import com.mkt.kernel.trace.TraceIds;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Fills a missing Result.traceId from MDC so the envelope always matches the header. */
@RestControllerAdvice
public class ResultTraceAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return Result.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(body instanceof Result<?> result)) {
            return body;
        }
        String traceId = result.traceId();
        if (traceId != null && !traceId.isBlank()) {
            return result;
        }
        return new Result<>(result.code(), result.message(), result.data(), TraceIds.current());
    }
}
