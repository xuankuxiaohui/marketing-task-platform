package com.mkt.identity.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.identity.application.IdentityAuditAppender;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.audit.Audited;
import com.mkt.kernel.audit.AuditOnce;
import com.mkt.kernel.json.JsonUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

class AuditedAspectTest {

    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private IdentityAuditAppender appender;
    private AuditedAspect aspect;

    @BeforeEach
    void setUp() {
        AuditOnce.clear();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(7L, "admin", "alice"));
        appender = new IdentityAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN));
        aspect = new AuditedAspect(appender, mock(PlatformTransactionManager.class));
    }

    @AfterEach
    void tearDown() {
        AuditOnce.clear();
        UserContext.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void aopWritesOneOutboxRowOnSuccess() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint(new CacheEvictBody("dict"));
        when(joinPoint.proceed()).thenReturn("ok");
        assertThat(aspect.around(joinPoint, audited("system", "cache-evict"))).isEqualTo("ok");
        assertThat(outbox.all()).hasSize(1);
        JsonNode payload = JsonUtil.readTree(outbox.all().get(0).payload());
        assertThat(payload.get("module").asString()).isEqualTo("system");
        assertThat(payload.get("action").asString()).isEqualTo("cache-evict");
        assertThat(payload.get("operatorId").asLong()).isEqualTo(7L);
        assertThat(payload.get("result").asString()).isEqualTo("SUCCESS");
        assertThat(payload.get("requestSummary").asString()).contains("dict");
    }

    @Test
    void leftoverMarkFromPriorRequestDoesNotSkipThisWrite() throws Throwable {
        AuditOnce.mark();
        ProceedingJoinPoint joinPoint = joinPoint(new CacheEvictBody("dict"));
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.around(joinPoint, audited("system", "cache-evict"));
        assertThat(outbox.all()).hasSize(1);
    }

    @Test
    void alreadyAppendedPathDoesNotInsertSecondRow() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint(new CacheEvictBody("dict"));
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            appender.append("system", "cache-evict", "cache", "dict", "SUCCESS", "{\"namespace\":\"dict\"}");
            return "ok";
        });
        aspect.around(joinPoint, audited("system", "cache-evict"));
        assertThat(outbox.all()).hasSize(1);
        JsonNode payload = JsonUtil.readTree(outbox.all().get(0).payload());
        assertThat(payload.get("requestSummary").asString()).contains("namespace");
    }

    @Test
    void failureStillWritesWhenServiceDidNotAppend() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint(new CacheEvictBody("nope"));
        when(joinPoint.proceed()).thenThrow(new BusinessException(CommonErrorCodes.PARAM_INVALID));
        assertThatThrownBy(() -> aspect.around(joinPoint, audited("system", "cache-evict")))
                .isInstanceOf(BusinessException.class);
        assertThat(outbox.all()).hasSize(1);
        JsonNode payload = JsonUtil.readTree(outbox.all().get(0).payload());
        assertThat(payload.get("result").asString()).isEqualTo("FAILURE");
        assertThat(payload.get("errorMessage").asString()).isNotBlank();
    }

    @Test
    void loginPasswordIsMaskedWhenAopWrites() throws Throwable {
        ProceedingJoinPoint joinPoint =
                joinPoint(new AdminLoginBody("alice", "Abcdef12!x"));
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.around(joinPoint, audited("auth", "login"));
        JsonNode payload = JsonUtil.readTree(outbox.all().get(0).payload());
        String summary = payload.get("requestSummary").asString();
        assertThat(summary).contains("alice");
        assertThat(summary).doesNotContain("Abcdef12!x");
    }

    private static ProceedingJoinPoint joinPoint(Object arg) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[] {arg});
        return joinPoint;
    }

    private static Audited audited(String module, String action) {
        Audited audited = mock(Audited.class);
        when(audited.module()).thenReturn(module);
        when(audited.action()).thenReturn(action);
        return audited;
    }

    private record CacheEvictBody(String namespace) {}

    private record AdminLoginBody(String username, String password) {}
}
