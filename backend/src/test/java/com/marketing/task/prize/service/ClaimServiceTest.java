package com.marketing.task.prize.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.service.EventTrackingService;
import com.marketing.task.prize.domain.entity.PrizeClaimLock;
import com.marketing.task.prize.domain.entity.PrizeRecord;
import com.marketing.task.prize.domain.enums.PrizeRecordStatus;
import com.marketing.task.prize.domain.enums.PrizeType;
import com.marketing.task.prize.mapper.PrizeClaimLockMapper;
import com.marketing.task.prize.mapper.PrizeMapper;
import com.marketing.task.prize.mapper.PrizeRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock private PrizeRecordMapper recordMapper;
    @Mock private PrizeMapper prizeMapper;
    @Mock private PrizeClaimLockMapper lockMapper;
    @Mock private ApplicationContext applicationContext;
    @Mock private PrizeHandler prizeHandler;
    @Mock private EventTrackingService eventTrackingService;

    private ClaimService claimService;
    private PrizeRecord record;
    private Prize prize;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, PrizeClaimLock.class);
    }

    @BeforeEach
    void setUp() {
        claimService = new ClaimService(recordMapper, prizeMapper, lockMapper, applicationContext, eventTrackingService);

        record = new PrizeRecord();
        record.setId(1L);
        record.setUserId("u_test");
        record.setPrizeId(10L);
        record.setStatus(PrizeRecordStatus.WON.name());
        record.setExpireTime(LocalDateTime.now().plusDays(7));
        record.setRetryCount(0);

        prize = new Prize();
        prize.setId(10L);
        prize.setHandlerBean("pointPrizeHandler");
        prize.setMaxRetry(3);
        prize.setParamsJson("{}");

        when(recordMapper.selectById(1L)).thenReturn(record);
        lenient().when(recordMapper.updateById(any(PrizeRecord.class))).thenReturn(1);
    }

    // ---- Happy path ----

    @Test
    void claim_shouldGrantSuccessfully() {
        when(lockMapper.insert(any(PrizeClaimLock.class))).thenReturn(1);
        when(prizeMapper.selectById(10L)).thenReturn(prize);
        when(applicationContext.getBean("pointPrizeHandler", PrizeHandler.class)).thenReturn(prizeHandler);
        when(prizeHandler.grant(any(PrizeRecord.class), any(Prize.class))).thenReturn(GrantResult.success("TRADE-001"));
        when(lockMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        ClaimResult result = claimService.claim(1L);

        assertEquals("GRANTED", result.getStatus());
        assertEquals("TRADE-001", result.getTradeNo());
    }

    // ---- Already granted ----

    @Test
    void claim_shouldReturnAlreadyGranted() {
        record.setStatus(PrizeRecordStatus.GRANTED.name());
        record.setExternalTradeNo("TRADE-EXISTING");

        ClaimResult result = claimService.claim(1L);

        assertEquals("GRANTED", result.getStatus());
        assertEquals("TRADE-EXISTING", result.getTradeNo());
        verify(lockMapper, never()).insert(any(PrizeClaimLock.class));
    }

    // ---- Expired ----

    @Test
    void claim_shouldRejectWhenExpired() {
        record.setExpireTime(LocalDateTime.now().minusDays(1));

        ClaimResult result = claimService.claim(1L);

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getErrorMessage().contains("过期"));
    }

    // ---- Lock contention ----

    @Test
    void claim_shouldReturnInProgressWhenLocked() {
        when(lockMapper.insert(any(PrizeClaimLock.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));

        ClaimResult result = claimService.claim(1L);

        assertEquals("CLAIMING", result.getStatus());
        verify(recordMapper, never()).updateById(any(PrizeRecord.class));
    }

    // ---- Handler failure ----

    @Test
    void claim_shouldRecordFailureOnHandlerError() {
        when(lockMapper.insert(any(PrizeClaimLock.class))).thenReturn(1);
        when(prizeMapper.selectById(10L)).thenReturn(prize);
        when(applicationContext.getBean("pointPrizeHandler", PrizeHandler.class)).thenReturn(prizeHandler);
        when(prizeHandler.grant(record, prize)).thenReturn(GrantResult.fail("API超时"));
        when(lockMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        ClaimResult result = claimService.claim(1L);

        assertEquals("FAILED", result.getStatus());
        assertEquals("API超时", result.getErrorMessage());
    }

    // ---- Handler exception ----

    @Test
    void claim_shouldRecordFailureOnException() {
        when(lockMapper.insert(any(PrizeClaimLock.class))).thenReturn(1);
        when(prizeMapper.selectById(10L)).thenReturn(prize);
        when(applicationContext.getBean("pointPrizeHandler", PrizeHandler.class)).thenReturn(prizeHandler);
        when(prizeHandler.grant(record, prize)).thenThrow(new RuntimeException("网络异常"));
        when(lockMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        ClaimResult result = claimService.claim(1L);

        assertEquals("FAILED", result.getStatus());
        assertEquals("奖品发放失败，请联系客服", result.getErrorMessage());
    }

    // ---- Max retry → permanent failure ----

    @Test
    void claim_shouldMarkPermanentFailureWhenMaxRetryExceeded() {
        record.setRetryCount(2);
        prize.setMaxRetry(3);

        when(lockMapper.insert(any(PrizeClaimLock.class))).thenReturn(1);
        when(prizeMapper.selectById(10L)).thenReturn(prize);
        when(applicationContext.getBean("pointPrizeHandler", PrizeHandler.class)).thenReturn(prizeHandler);
        when(prizeHandler.grant(record, prize)).thenReturn(GrantResult.fail("再次失败"));
        when(lockMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        claimService.claim(1L);

        verify(recordMapper, atLeastOnce()).updateById(any(PrizeRecord.class));
    }

    // ---- FAILED status can retry ----

    @Test
    void claim_shouldAllowRetryWhenPreviousFailed() {
        record.setStatus(PrizeRecordStatus.FAILED.name());
        record.setRetryCount(1);

        when(lockMapper.insert(any(PrizeClaimLock.class))).thenReturn(1);
        when(prizeMapper.selectById(10L)).thenReturn(prize);
        when(applicationContext.getBean("pointPrizeHandler", PrizeHandler.class)).thenReturn(prizeHandler);
        when(prizeHandler.grant(record, prize)).thenReturn(GrantResult.success("TRADE-RETRY"));
        when(lockMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        ClaimResult result = claimService.claim(1L);

        assertEquals("GRANTED", result.getStatus());
        assertEquals("TRADE-RETRY", result.getTradeNo());
    }

    // ---- FAILED_PERMANENTLY cannot claim ----

    @Test
    void claim_shouldRejectPermanentFailure() {
        record.setStatus(PrizeRecordStatus.FAILED_PERMANENTLY.name());

        ClaimResult result = claimService.claim(1L);

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getErrorMessage().contains("永久失败"));
        verify(lockMapper, never()).insert(any(PrizeClaimLock.class));
    }
}
