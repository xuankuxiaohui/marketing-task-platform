package com.mkt.kernel.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.RateLimitedException;
import com.mkt.kernel.Result;
import com.mkt.kernel.trace.TraceIds;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        TraceIds.clear();
    }

    @Test
    void businessUsesErrorCodeHttpAndMessage() {
        TraceIds.put("t-biz");
        ResponseEntity<Result<Void>> response =
                handler.handleBusiness(new BusinessException(CommonErrorCodes.NOT_FOUND, "资源不存在"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("common.not-found");
        assertThat(response.getBody().message()).isEqualTo("资源不存在");
        assertThat(response.getBody().traceId()).isEqualTo("t-biz");
    }

    @Test
    void validationJoinsFieldErrors() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "cmd");
        binding.addError(new FieldError("cmd", "page", "must be >= 1"));
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("toString"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<Result<Void>> response = handler.handleMethodArgumentNotValid(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("common.param-invalid");
        assertThat(response.getBody().message()).isEqualTo("page: must be >= 1");
    }

    @Test
    void constraintViolationUsesPropertyPath() {
        ConstraintViolation<?> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        org.mockito.Mockito.when(path.toString()).thenReturn("pageSize");
        org.mockito.Mockito.when(violation.getPropertyPath()).thenReturn(path);
        org.mockito.Mockito.when(violation.getMessage()).thenReturn("must be <= 100");

        ResponseEntity<Result<Void>> response =
                handler.handleConstraint(new ConstraintViolationException(Set.of(violation)));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("pageSize: must be <= 100");
    }

    @Test
    void rateLimitedSetsRetryAfter() {
        ResponseEntity<Result<Void>> response =
                handler.handleBusiness(new RateLimitedException(CommonErrorCodes.RATE_LIMITED, 60));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody().code()).isEqualTo("common.rate-limited");
    }

    @Test
    void unknownExceptionHidesCause() {
        ResponseEntity<Result<Void>> response = handler.handleUnknown(new IllegalStateException("jdbc leaked"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("common.server-error");
        assertThat(response.getBody().message()).isEqualTo(CommonErrorCodes.SERVER_ERROR.message());
        assertThat(response.getBody().message()).doesNotContain("jdbc");
    }

    @Test
    void unreadableBodyIsParamInvalidWithoutCause() {
        HttpInputMessage body = org.mockito.Mockito.mock(HttpInputMessage.class);
        ResponseEntity<Result<Void>> response =
                handler.handleUnreadable(new HttpMessageNotReadableException("JSON parse error at line 1", body));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("common.param-invalid");
        assertThat(response.getBody().message()).isEqualTo(CommonErrorCodes.PARAM_INVALID.message());
        assertThat(response.getBody().message()).doesNotContain("JSON parse");
    }

    @Test
    void notFoundMapsToCommonNotFound() {
        ResponseEntity<Result<Void>> response =
                handler.handleNotFound(new NoHandlerFoundException("GET", "/missing", org.springframework.http.HttpHeaders.EMPTY));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("common.not-found");
    }

    @Test
    void bindExceptionJoinsFieldErrors() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "query");
        binding.addError(new FieldError("query", "pageSize", "must be <= 100"));
        org.springframework.validation.BindException ex =
                new org.springframework.validation.BindException(binding);

        ResponseEntity<Result<Void>> response = handler.handleBind(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("pageSize: must be <= 100");
    }
}
