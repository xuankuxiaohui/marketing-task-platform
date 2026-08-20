package com.mkt.identity.audit;

import com.mkt.identity.application.IdentityAuditAppender;
import com.mkt.identity.support.ClientIp;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.audit.Audited;
import com.mkt.kernel.audit.AuditOnce;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Around {@link Audited}: one Outbox {@code audit.log} per write (design §6.5). Skips if a
 * service appender already marked {@link AuditOnce}.
 */
@Aspect
public class AuditedAspect {

    private final IdentityAuditAppender audits;
    private final PlatformTransactionManager transactionManager;

    public AuditedAspect(IdentityAuditAppender audits, PlatformTransactionManager transactionManager) {
        this.audits = audits;
        this.transactionManager = transactionManager;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        AuditOnce.clear();
        long started = System.nanoTime();
        String summary = AuditSummaries.ofArgs(joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            record(audited, "SUCCESS", summary, started, null);
            return result;
        } catch (Throwable ex) {
            record(audited, "FAILURE", summary, started, errorMessage(ex));
            throw ex;
        } finally {
            AuditOnce.clear();
        }
    }

    private void record(Audited audited, String result, String summary, long startedNanos, String error) {
        if (AuditOnce.written()) {
            return;
        }
        int costMs = (int) Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        HttpServletRequest request = currentRequest();
        String ip = ClientIp.of(request);
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        Runnable write = () -> audits.append(
                audited.module(),
                audited.action(),
                audited.module(),
                null,
                result,
                ip,
                userAgent,
                summary,
                costMs,
                error);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            write.run();
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> write.run());
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest();
    }

    private static String errorMessage(Throwable ex) {
        if (ex instanceof BusinessException business) {
            return business.getMessage();
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
