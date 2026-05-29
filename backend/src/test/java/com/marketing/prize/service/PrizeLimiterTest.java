package com.marketing.prize.service;

import com.marketing.context.UserContext;
import com.marketing.prize.domain.entity.Prize;
import com.marketing.prize.domain.entity.PrizeInventoryRecord;
import com.marketing.prize.domain.entity.PrizeRecord;
import com.marketing.prize.domain.enums.PrizeRecordStatus;
import com.marketing.prize.mapper.PrizeInventoryRecordMapper;
import com.marketing.prize.mapper.PrizeRecordMapper;
import com.marketing.prize.service.limiters.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrizeLimiterTest {

    @Mock private PrizeInventoryRecordMapper inventoryMapper;
    @Mock private PrizeRecordMapper recordMapper;

    private Prize prize;
    private UserContext user;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        prize = new Prize();
        prize.setId(1L);
        prize.setEnabled(true);
        prize.setTotalStock(100);
        prize.setMonthlyStock(50);
        prize.setDailyStock(10);
        prize.setUserTotalLimit(3);
        prize.setUserMonthlyLimit(2);
        prize.setUserDailyLimit(1);

        user = UserContext.builder()
                .userId("u_test").province("BJ").level(5)
                .tags(Set.of("vip")).build();
        now = LocalDateTime.now();
    }

    // ---- PrizeStatusLimiter ----

    @Test
    void statusLimiter_shouldPassWhenEnabled() {
        PrizeStatusLimiter limiter = new PrizeStatusLimiter();
        assertTrue(limiter.check(user, prize, now).isEmpty());
    }

    @Test
    void statusLimiter_shouldRejectWhenDisabled() {
        prize.setEnabled(false);
        PrizeStatusLimiter limiter = new PrizeStatusLimiter();
        assertTrue(limiter.check(user, prize, now).isPresent());
    }

    @Test
    void statusLimiter_shouldRejectBeforeStartTime() {
        prize.setStartTime(LocalDateTime.now().plusDays(1));
        PrizeStatusLimiter limiter = new PrizeStatusLimiter();
        assertTrue(limiter.check(user, prize, now).isPresent());
    }

    @Test
    void statusLimiter_shouldRejectAfterEndTime() {
        prize.setEndTime(LocalDateTime.now().minusDays(1));
        PrizeStatusLimiter limiter = new PrizeStatusLimiter();
        assertTrue(limiter.check(user, prize, now).isPresent());
    }

    // ---- InventoryLimiter ----

    @Test
    void inventoryLimiter_shouldPassWhenStockAvailable() {
        when(inventoryMapper.selectList(any())).thenReturn(List.of());

        InventoryLimiter limiter = new InventoryLimiter(inventoryMapper);
        assertTrue(limiter.check(user, prize, now).isEmpty());
    }

    @Test
    void inventoryLimiter_shouldRejectWhenTotalStockExhausted() {
        PrizeInventoryRecord r = new PrizeInventoryRecord();
        r.setPrizeId(1L);
        r.setQuantity(100);
        r.setCreatedAt(now);
        when(inventoryMapper.selectList(any())).thenReturn(List.of(r));

        InventoryLimiter limiter = new InventoryLimiter(inventoryMapper);
        assertTrue(limiter.check(user, prize, now).isPresent());
    }

    @Test
    void inventoryLimiter_shouldIgnoreExpiredCycleRecords() {
        // Old month record shouldn't count toward current month
        PrizeInventoryRecord oldRecord = new PrizeInventoryRecord();
        oldRecord.setPrizeId(1L);
        oldRecord.setQuantity(50);
        oldRecord.setCreatedAt(now.minusMonths(2));
        when(inventoryMapper.selectList(any())).thenReturn(List.of(oldRecord));

        InventoryLimiter limiter = new InventoryLimiter(inventoryMapper);
        assertTrue(limiter.check(user, prize, now).isEmpty());
    }

    // ---- ProvinceLimiter ----

    @Test
    void provinceLimiter_shouldPassWhenUserInAllowList() {
        prize.setLimitsJson("{\"provinces\":{\"type\":\"allow\",\"list\":[\"BJ\",\"SH\"]}}");
        ProvinceLimiter limiter = new ProvinceLimiter();
        assertTrue(limiter.check(user, prize, now).isEmpty());
    }

    @Test
    void provinceLimiter_shouldRejectWhenUserNotInAllowList() {
        prize.setLimitsJson("{\"provinces\":{\"type\":\"allow\",\"list\":[\"SH\",\"GZ\"]}}");
        ProvinceLimiter limiter = new ProvinceLimiter();
        assertTrue(limiter.check(user, prize, now).isPresent());
    }

    @Test
    void provinceLimiter_shouldPassWhenNoLimitsSet() {
        prize.setLimitsJson(null);
        ProvinceLimiter limiter = new ProvinceLimiter();
        assertTrue(limiter.check(user, prize, now).isEmpty());
    }

    // ---- LevelLimiter ----

    @Test
    void levelLimiter_shouldRejectWhenBelowMinLevel() {
        prize.setLimitsJson("{\"min_level\":10}");
        LevelLimiter limiter = new LevelLimiter();
        assertTrue(limiter.check(user, prize, now).isPresent());
    }

    @Test
    void levelLimiter_shouldPassWhenAboveMinLevel() {
        prize.setLimitsJson("{\"min_level\":3}");
        LevelLimiter limiter = new LevelLimiter();
        assertTrue(limiter.check(user, prize, now).isEmpty());
    }

    // ---- TagLimiter ----

    @Test
    void tagLimiter_shouldRejectWhenUserHasDenyTag() {
        prize.setLimitsJson("{\"tags\":{\"type\":\"deny\",\"list\":[\"vip\"]}}");
        TagLimiter limiter = new TagLimiter();
        assertTrue(limiter.check(user, prize, now).isPresent());
    }

    @Test
    void tagLimiter_shouldPassWhenUserHasAllowTag() {
        prize.setLimitsJson("{\"tags\":{\"type\":\"allow\",\"list\":[\"vip\"]}}");
        TagLimiter limiter = new TagLimiter();
        assertTrue(limiter.check(user, prize, now).isEmpty());
    }

    @Test
    void tagLimiter_shouldPassWhenNoTagsSet() {
        prize.setLimitsJson(null);
        TagLimiter limiter = new TagLimiter();
        assertTrue(limiter.check(user, prize, now).isEmpty());
    }

    // ---- MutexLimiter ----

    @Test
    void mutexLimiter_shouldRejectWhenConflictExists() {
        prize.setLimitsJson("{\"mutex_prize_ids\":[2, 3]}");
        PrizeRecord conflict = new PrizeRecord();
        conflict.setPrizeId(2L);
        conflict.setStatus(PrizeRecordStatus.WON.name());

        when(recordMapper.selectList(any())).thenReturn(List.of(conflict));

        MutexLimiter limiter = new MutexLimiter(recordMapper);
        assertTrue(limiter.check(user, prize, now).isPresent());
    }

    @Test
    void mutexLimiter_shouldPassWhenNoConflict() {
        prize.setLimitsJson("{\"mutex_prize_ids\":[2, 3]}");
        when(recordMapper.selectList(any())).thenReturn(List.of());

        MutexLimiter limiter = new MutexLimiter(recordMapper);
        assertTrue(limiter.check(user, prize, now).isEmpty());
    }
}
