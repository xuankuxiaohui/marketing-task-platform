package com.marketing.prize.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.common.BusinessException;
import com.marketing.context.UserContext;
import com.marketing.prize.domain.entity.*;
import com.marketing.prize.domain.enums.ClaimExpireType;
import com.marketing.prize.domain.enums.PrizeRecordStatus;
import com.marketing.prize.mapper.PrizeInventoryRecordMapper;
import com.marketing.prize.mapper.PrizeMapper;
import com.marketing.prize.mapper.PrizeRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrizeServiceTest {

    @Mock private PrizeMapper prizeMapper;
    @Mock private PrizeRecordMapper recordMapper;
    @Mock private PrizeInventoryRecordMapper inventoryMapper;
    @Mock private PrizeLimiter limiter1;
    @Mock private PrizeLimiter limiter2;
    @Mock private ClaimService claimService;

    @Captor private ArgumentCaptor<PrizeRecord> recordCaptor;
    @Captor private ArgumentCaptor<PrizeInventoryRecord> inventoryCaptor;

    private PrizeService prizeService;
    private UserContext user;
    private Prize prize;
    private GrantContext ctx;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, Prize.class);
        TableInfoHelper.initTableInfo(assistant, PrizeRecord.class);
        TableInfoHelper.initTableInfo(assistant, PrizeInventoryRecord.class);
        TableInfoHelper.initTableInfo(assistant, PrizeClaimLock.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(limiter1.check(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(limiter2.check(any(), any(), any())).thenReturn(Optional.empty());
        prizeService = new PrizeService(prizeMapper, recordMapper, inventoryMapper,
                List.of(limiter1, limiter2), claimService);

        user = UserContext.builder()
                .userId("u_test").province("BJ").level(5)
                .tags(Set.of("vip")).build();

        prize = new Prize();
        prize.setId(1L);
        prize.setType("POINT");
        prize.setName("新人积分包");
        prize.setEnabled(true);
        prize.setAutoGrant(false);
        prize.setClaimExpireType("DAYS");
        prize.setClaimExpireValue("7");
        prize.setParamsJson("{\"type\":\"POINT\",\"amount\":10}");
        prize.setHandlerBean("pointPrizeHandler");
        prize.setTotalStock(1000);
        prize.setMonthlyStock(100);
        prize.setDailyStock(50);
        prize.setUserTotalLimit(3);
        prize.setMaxRetry(3);

        ctx = GrantContext.builder()
                .instanceId(100L).taskId(1L).stepId(20L)
                .cycleKey("20260524").province("BJ").level(5).build();
    }

    // ---- Idempotency ----

    @Test
    void grant_shouldSkipWhenAlreadyGranted() {
        PrizeRecord existing = new PrizeRecord();
        existing.setId(1L);
        existing.setStatus(PrizeRecordStatus.GRANTED.name());

        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        PrizeRecord result = prizeService.grant(user, 1L, 1, ctx);

        assertEquals(PrizeRecordStatus.GRANTED.name(), result.getStatus());
        verify(prizeMapper, never()).selectById(any());
    }

    @Test
    void grant_shouldSkipWhenAlreadyWon() {
        PrizeRecord existing = new PrizeRecord();
        existing.setId(1L);
        existing.setStatus(PrizeRecordStatus.WON.name());

        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        PrizeRecord result = prizeService.grant(user, 1L, 1, ctx);

        assertEquals(PrizeRecordStatus.WON.name(), result.getStatus());
        verify(prizeMapper, never()).selectById(any());
    }

    // ---- Normal flow ----

    @Test
    void grant_shouldCreateRecordAndDeductInventory() {
        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(prizeMapper.selectById(1L)).thenReturn(prize);
        when(recordMapper.insert(any(PrizeRecord.class))).thenReturn(1);
        when(inventoryMapper.insert(any(PrizeInventoryRecord.class))).thenReturn(1);

        PrizeRecord result = prizeService.grant(user, 1L, 1, ctx);

        assertNotNull(result);
        verify(recordMapper).insert(recordCaptor.capture());
        PrizeRecord r = recordCaptor.getValue();
        assertEquals(PrizeRecordStatus.WON.name(), r.getStatus());
        assertEquals("u_test", r.getUserId());
        assertEquals(1L, r.getPrizeId());
        assertEquals("100:20:1", r.getIdempotentKey());
        assertEquals("POINT", r.getPrizeType());
        assertEquals("新人积分包", r.getPrizeName());
        assertNotNull(r.getExpireTime());

        verify(inventoryMapper).insert(inventoryCaptor.capture());
        assertEquals(1L, inventoryCaptor.getValue().getPrizeId());
        assertEquals(1, inventoryCaptor.getValue().getQuantity());
    }

    @Test
    void grant_shouldAutoClaimWhenAutoGrantEnabled() {
        prize.setAutoGrant(true);
        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(prizeMapper.selectById(1L)).thenReturn(prize);
        when(recordMapper.insert(any(PrizeRecord.class))).thenReturn(1);
        when(inventoryMapper.insert(any(PrizeInventoryRecord.class))).thenReturn(1);
        when(recordMapper.selectById(any())).thenAnswer(inv -> {
            PrizeRecord r = new PrizeRecord();
            r.setId(1L);
            r.setStatus(PrizeRecordStatus.GRANTED.name());
            r.setUserId("u_test");
            r.setPrizeId(1L);
            return r;
        });

        PrizeRecord result = prizeService.grant(user, 1L, 1, ctx);

        assertEquals(PrizeRecordStatus.GRANTED.name(), result.getStatus());
        verify(claimService).claim(any());
    }

    // ---- Limiter chain ----

    @Test
    void grant_shouldFailWhenLimiterRejects() {
        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(prizeMapper.selectById(1L)).thenReturn(prize);
        when(limiter1.check(any(), any(), any())).thenReturn(Optional.of("库存不足"));

        assertThrows(BusinessException.class, () -> prizeService.grant(user, 1L, 1, ctx));
        verify(recordMapper, never()).insert(any(PrizeRecord.class));
    }

    // ---- Prize not found ----

    @Test
    void grant_shouldThrowWhenPrizeNotFound() {
        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(prizeMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> prizeService.grant(user, 999L, 1, ctx));
    }

    // ---- Expire time calculation ----

    @Test
    void grant_shouldCalculateExpireByDays() {
        prize.setClaimExpireType("DAYS");
        prize.setClaimExpireValue("3");

        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(prizeMapper.selectById(1L)).thenReturn(prize);
        when(recordMapper.insert(any(PrizeRecord.class))).thenReturn(1);
        when(inventoryMapper.insert(any(PrizeInventoryRecord.class))).thenReturn(1);

        prizeService.grant(user, 1L, 1, ctx);

        verify(recordMapper).insert(recordCaptor.capture());
        LocalDateTime expireTime = recordCaptor.getValue().getExpireTime();
        LocalDateTime expected = LocalDateTime.now().plusDays(3);
        assertEquals(expected.toLocalDate(), expireTime.toLocalDate());
    }

    @Test
    void grant_shouldCalculateExpireByCalendarMonth() {
        prize.setClaimExpireType("CALENDAR_MONTH");
        prize.setClaimExpireValue("1");

        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(prizeMapper.selectById(1L)).thenReturn(prize);
        when(recordMapper.insert(any(PrizeRecord.class))).thenReturn(1);
        when(inventoryMapper.insert(any(PrizeInventoryRecord.class))).thenReturn(1);

        prizeService.grant(user, 1L, 1, ctx);

        verify(recordMapper).insert(recordCaptor.capture());
        LocalDateTime expireTime = recordCaptor.getValue().getExpireTime();
        assertNotNull(expireTime);
        assertTrue(expireTime.isAfter(LocalDateTime.now()));
    }

    @Test
    void grant_shouldCalculateExpireByFixedDate() {
        prize.setClaimExpireType("FIXED_DATE");
        prize.setClaimExpireValue("2026-12-31");

        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(prizeMapper.selectById(1L)).thenReturn(prize);
        when(recordMapper.insert(any(PrizeRecord.class))).thenReturn(1);
        when(inventoryMapper.insert(any(PrizeInventoryRecord.class))).thenReturn(1);

        prizeService.grant(user, 1L, 1, ctx);

        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals("2026-12-31T23:59:59", recordCaptor.getValue().getExpireTime().toString());
    }

    // ---- User records / status counts ----

    @Test
    void getUserRecords_shouldFilterByStatus() {
        PrizeRecord r = new PrizeRecord();
        r.setStatus(PrizeRecordStatus.WON.name());
        when(recordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));

        List<PrizeRecord> records = prizeService.getUserRecords("u_test", "WON");
        assertEquals(1, records.size());
    }

    @Test
    void getStatusCounts_shouldGroupByStatus() {
        PrizeRecord won = new PrizeRecord();
        won.setStatus(PrizeRecordStatus.WON.name());
        PrizeRecord granted = new PrizeRecord();
        granted.setStatus(PrizeRecordStatus.GRANTED.name());
        when(recordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(won, granted));

        var counts = prizeService.getStatusCounts("u_test");
        assertEquals(1L, counts.get("WON"));
        assertEquals(1L, counts.get("GRANTED"));
    }

    // ---- Idempotent key format ----

    @Test
    void grant_shouldFormatIdempotentKeyCorrectly() {
        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(prizeMapper.selectById(1L)).thenReturn(prize);
        when(recordMapper.insert(any(PrizeRecord.class))).thenReturn(1);
        when(inventoryMapper.insert(any(PrizeInventoryRecord.class))).thenReturn(1);

        ctx = GrantContext.builder().instanceId(999L).taskId(5L).stepId(888L).build();
        prizeService.grant(user, 1L, 1, ctx);

        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals("999:888:1", recordCaptor.getValue().getIdempotentKey());
    }

    // ---- Quantity support ----

    @Test
    void grant_shouldAcceptMultipleQuantity() {
        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(prizeMapper.selectById(1L)).thenReturn(prize);
        when(recordMapper.insert(any(PrizeRecord.class))).thenReturn(1);
        when(inventoryMapper.insert(any(PrizeInventoryRecord.class))).thenReturn(1);

        prizeService.grant(user, 1L, 5, ctx);

        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals(5, recordCaptor.getValue().getQuantity());
        verify(inventoryMapper).insert(inventoryCaptor.capture());
        assertEquals(5, inventoryCaptor.getValue().getQuantity());
    }
}
