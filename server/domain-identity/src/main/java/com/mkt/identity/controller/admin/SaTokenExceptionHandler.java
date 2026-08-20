package com.mkt.identity.controller.admin;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.mkt.identity.support.SessionAuthFilter;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.Result;
import com.mkt.kernel.SessionErrorCodes;
import com.mkt.kernel.trace.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SaTokenExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SaTokenExceptionHandler.class);

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Result<Void>> handleNotPermission(
            NotPermissionException ex, HttpServletRequest request) {
        request.setAttribute(SessionAuthFilter.RBAC_DENIED, Boolean.TRUE);
        log.warn(
                "permission denied, code={}, traceId={}",
                CommonErrorCodes.PERMISSION_DENIED.code(),
                TraceIds.current());
        return ResponseEntity.status(CommonErrorCodes.PERMISSION_DENIED.httpStatus())
                .body(Result.fail(CommonErrorCodes.PERMISSION_DENIED));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException ex) {
        log.warn(
                "session invalid, code={}, traceId={}", SessionErrorCodes.INVALID.code(), TraceIds.current());
        return ResponseEntity.status(SessionErrorCodes.INVALID.httpStatus())
                .body(Result.fail(SessionErrorCodes.INVALID));
    }
}
