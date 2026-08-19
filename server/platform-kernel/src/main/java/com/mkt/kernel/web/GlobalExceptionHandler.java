package com.mkt.kernel.web;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.Result;
import com.mkt.kernel.trace.TraceIds;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/** Unique {@code @RestControllerAdvice} (appendix C / docs/standards/09). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.errorCode();
        log.warn("business rejected, code={}, traceId={}", errorCode.code(), TraceIds.current());
        return ResponseEntity.status(errorCode.httpStatus()).body(Result.fail(errorCode, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return paramInvalid(joinFieldErrors(ex));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        return paramInvalid(joinFieldErrors(ex));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::formatViolation)
                .collect(Collectors.joining("; "));
        return paramInvalid(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("unreadable body, traceId={}", TraceIds.current());
        return paramInvalid(CommonErrorCodes.PARAM_INVALID.message());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoHandlerFoundException ex) {
        log.info("no handler, path={}, traceId={}", ex.getRequestURL(), TraceIds.current());
        return ResponseEntity.status(CommonErrorCodes.NOT_FOUND.httpStatus())
                .body(Result.fail(CommonErrorCodes.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("unhandled exception, traceId={}", TraceIds.current(), ex);
        return ResponseEntity.status(CommonErrorCodes.SERVER_ERROR.httpStatus())
                .body(Result.fail(CommonErrorCodes.SERVER_ERROR));
    }

    private static ResponseEntity<Result<Void>> paramInvalid(String message) {
        String body = message == null || message.isBlank()
                ? CommonErrorCodes.PARAM_INVALID.message()
                : message;
        return ResponseEntity.status(CommonErrorCodes.PARAM_INVALID.httpStatus())
                .body(Result.fail(CommonErrorCodes.PARAM_INVALID, body));
    }

    private static String joinFieldErrors(BindException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
    }

    private static String formatFieldError(FieldError error) {
        String defaultMessage = error.getDefaultMessage();
        String detail = defaultMessage == null || defaultMessage.isBlank() ? "invalid" : defaultMessage;
        return error.getField() + ": " + detail;
    }

    private static String formatViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "value" : violation.getPropertyPath().toString();
        return path + ": " + violation.getMessage();
    }
}
