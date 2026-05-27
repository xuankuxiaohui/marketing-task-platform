package com.marketing.task.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.AccessDeniedException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        if (ec.getHttpStatus() >= 500) {
            log.error("BusinessException [{}] {} - {}", ec.getSubCode(), ec.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("BusinessException [{}] {} - {}", ec.getSubCode(), ec.getCode(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Result.fail(ec, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        String message = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数错误" : error.getDefaultMessage())
                .orElse("参数错误");
        log.warn("Validation failed: {}", fields);
        return ResponseEntity.status(400).body(Result.fail(ErrorCode.BAD_REQUEST, message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        String message = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数错误" : error.getDefaultMessage())
                .orElse("参数错误");
        log.warn("Bind validation failed: {}", fields);
        return ResponseEntity.status(400).body(Result.fail(ErrorCode.BAD_REQUEST, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("JSON deserialization failed — body may contain wrong types or unknown fields: {}", ex.getMessage());
        return ResponseEntity.status(400).body(Result.fail(ErrorCode.BAD_REQUEST, "请求体格式错误"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Route not found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(Result.fail(ErrorCode.NOT_FOUND, "接口不存在"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(403).body(Result.fail(ErrorCode.FORBIDDEN, "无权限访问"));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException ex) {
        log.warn("Auth required — token missing, expired, or invalid: {}", ex.getMessage());
        return ResponseEntity.status(401).body(Result.fail(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录"));
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Result<Void>> handleNotPermission(NotPermissionException ex) {
        log.warn("Permission denied: {}", ex.getMessage());
        return ResponseEntity.status(403).body(Result.fail(ErrorCode.FORBIDDEN, "无权限"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(500)
                .body(Result.fail(ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }
}
