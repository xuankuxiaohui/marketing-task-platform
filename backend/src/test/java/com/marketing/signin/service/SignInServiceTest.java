package com.marketing.signin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.common.BusinessException;
import com.marketing.signin.domain.dto.SignInResult;
import com.marketing.signin.domain.dto.SignInStatusVO;
import com.marketing.signin.domain.entity.PointAccount;
import com.marketing.signin.domain.entity.SignInConfig;
import com.marketing.signin.domain.entity.SignInRecord;
import com.marketing.signin.domain.enums.SignInConfigStatus;
import com.marketing.signin.mapper.SignInConfigMapper;
import com.marketing.signin.mapper.SignInRecordMapper;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignInServiceTest {

    @Mock
    private SignInConfigMapper configMapper;
    @Mock
    private SignInRecordMapper recordMapper;
    @Mock
    private PointService pointService;

    @Captor
    private ArgumentCaptor<SignInRecord> recordCaptor;

    private SignInService signInService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, SignInConfig.class);
        TableInfoHelper.initTableInfo(assistant, SignInRecord.class);
        TableInfoHelper.initTableInfo(assistant, PointAccount.class);
    }

    @BeforeEach
    void setUp() {
        signInService = new SignInService(configMapper, recordMapper, pointService, objectMapper);
    }

    private SignInConfig createConfig(Long id, String periodType, int basePoints,
                                      String streakConfig, Integer expireDays,
                                      boolean catchUpEnabled, int catchUpCost, Integer catchUpMaxDays) {
        SignInConfig config = new SignInConfig();
        config.setId(id);
        config.setName("测试签到");
        config.setStatus(SignInConfigStatus.PUBLISHED.name());
        config.setPeriodType(periodType);
        config.setBasePoints(basePoints);
        config.setStreakConfig(streakConfig);
        config.setPointExpireDays(expireDays);
        config.setCatchUpEnabled(catchUpEnabled);
        config.setCatchUpCost(catchUpCost);
        config.setCatchUpMaxDays(catchUpMaxDays);
        config.setStartTime(LocalDateTime.now().minusDays(1));
        config.setEndTime(LocalDateTime.now().plusDays(30));
        return config;
    }

    @Test
    void signIn_firstTime_returnsBasePoints() {
        SignInConfig config = createConfig(1L, "MONTHLY", 10, null, null, false, 0, null);
        doReturn(config).when(configMapper).selectById(1L);
        doReturn(null).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));
        doAnswer(inv -> {
            SignInRecord r = inv.getArgument(0);
            r.setId(100L);
            return 1;
        }).when(recordMapper).insert(any(SignInRecord.class));
        PointAccount account = new PointAccount();
        account.setBalance(10L);
        doReturn(account).when(pointService).getBalance("user1");

        SignInResult result = signInService.signIn(1L, "user1");

        assertTrue(result.isSuccess());
        assertEquals(1, result.getStreakDay());
        assertEquals(10, result.getBasePoints());
        assertEquals(0, result.getBonusPoints());
        assertEquals(10, result.getTotalPoints());
        assertNull(result.getTierReached());
        assertFalse(result.isCatchUp());

        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals(1, recordCaptor.getValue().getStreakDay());
        assertFalse(recordCaptor.getValue().getCatchUp());
    }

    @Test
    void signIn_consecutiveDays_incrementsStreak() {
        SignInConfig config = createConfig(1L, "MONTHLY", 10, null, null, false, 0, null);
        doReturn(config).when(configMapper).selectById(1L);

        SignInRecord yesterday = new SignInRecord();
        yesterday.setSigninDate(LocalDate.now().minusDays(1));
        yesterday.setStreakDay(3);

        // findRecord uses selectOne → null (no existing record for today)
        doReturn(null).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));
        // calculateStreak uses selectList → returns yesterday's record
        doReturn(List.of(yesterday)).when(recordMapper).selectList(any(LambdaQueryWrapper.class));
        doAnswer(inv -> {
            SignInRecord r = inv.getArgument(0);
            r.setId(101L);
            return 1;
        }).when(recordMapper).insert(any(SignInRecord.class));
        PointAccount account = new PointAccount();
        account.setBalance(0L);
        doReturn(account).when(pointService).getBalance("user1");

        SignInResult result = signInService.signIn(1L, "user1");

        assertTrue(result.isSuccess());
        assertEquals(4, result.getStreakDay());
    }

    @Test
    void signIn_gapInStreak_resetsToStreak1() {
        SignInConfig config = createConfig(1L, "MONTHLY", 10, null, null, false, 0, null);
        doReturn(config).when(configMapper).selectById(1L);

        SignInRecord oldRecord = new SignInRecord();
        oldRecord.setSigninDate(LocalDate.now().minusDays(3));
        oldRecord.setStreakDay(5);

        doReturn(null).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(List.of(oldRecord)).when(recordMapper).selectList(any(LambdaQueryWrapper.class));
        doAnswer(inv -> {
            SignInRecord r = inv.getArgument(0);
            r.setId(102L);
            return 1;
        }).when(recordMapper).insert(any(SignInRecord.class));
        PointAccount account = new PointAccount();
        account.setBalance(0L);
        doReturn(account).when(pointService).getBalance("user1");

        SignInResult result = signInService.signIn(1L, "user1");

        assertTrue(result.isSuccess());
        assertEquals(1, result.getStreakDay());
    }

    @Test
    void signIn_alreadySignedToday_returnsExisting() {
        SignInConfig config = createConfig(1L, "MONTHLY", 10, null, null, false, 0, null);
        doReturn(config).when(configMapper).selectById(1L);

        SignInRecord existing = new SignInRecord();
        existing.setId(50L);
        existing.setStreakDay(5);
        existing.setBasePoints(10);
        existing.setBonusPoints(20);
        existing.setTotalPoints(30);
        existing.setTierReached(7);
        existing.setCatchUp(false);
        doReturn(existing).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));

        PointAccount account = new PointAccount();
        account.setBalance(30L);
        doReturn(account).when(pointService).getBalance("user1");

        SignInResult result = signInService.signIn(1L, "user1");

        assertTrue(result.isSuccess());
        assertEquals(50L, result.getRecordId());
        assertEquals(5, result.getStreakDay());
        verify(recordMapper, never()).insert(any(SignInRecord.class));
    }

    @Test
    void signIn_withStreakBonus_grantsBonusPoints() throws Exception {
        String streakConfig = "{\"maxStreak\":30,\"tiers\":[{\"day\":3,\"bonus\":20},{\"day\":7,\"bonus\":50}]}";
        SignInConfig config = createConfig(1L, "MONTHLY", 10, streakConfig, null, false, 0, null);
        doReturn(config).when(configMapper).selectById(1L);

        SignInRecord yesterday = new SignInRecord();
        yesterday.setSigninDate(LocalDate.now().minusDays(1));
        yesterday.setStreakDay(2);

        doReturn(null).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(List.of(yesterday)).when(recordMapper).selectList(any(LambdaQueryWrapper.class));
        doAnswer(inv -> {
            SignInRecord r = inv.getArgument(0);
            r.setId(103L);
            return 1;
        }).when(recordMapper).insert(any(SignInRecord.class));
        PointAccount account = new PointAccount();
        account.setBalance(0L);
        doReturn(account).when(pointService).getBalance("user1");

        SignInResult result = signInService.signIn(1L, "user1");

        assertTrue(result.isSuccess());
        assertEquals(3, result.getStreakDay());
        assertEquals(10, result.getBasePoints());
        assertEquals(20, result.getBonusPoints());
        assertEquals(30, result.getTotalPoints());
        assertEquals(3, result.getTierReached());
    }

    @Test
    void signIn_maxStreakCap_capsAtMax() throws Exception {
        String streakConfig = "{\"maxStreak\":7,\"tiers\":[{\"day\":3,\"bonus\":20},{\"day\":7,\"bonus\":50}]}";
        SignInConfig config = createConfig(1L, "MONTHLY", 10, streakConfig, null, false, 0, null);
        doReturn(config).when(configMapper).selectById(1L);

        SignInRecord yesterday = new SignInRecord();
        yesterday.setSigninDate(LocalDate.now().minusDays(1));
        yesterday.setStreakDay(7);

        doReturn(null).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(List.of(yesterday)).when(recordMapper).selectList(any(LambdaQueryWrapper.class));
        doAnswer(inv -> {
            SignInRecord r = inv.getArgument(0);
            r.setId(104L);
            return 1;
        }).when(recordMapper).insert(any(SignInRecord.class));
        PointAccount account = new PointAccount();
        account.setBalance(0L);
        doReturn(account).when(pointService).getBalance("user1");

        SignInResult result = signInService.signIn(1L, "user1");

        assertTrue(result.isSuccess());
        assertEquals(7, result.getStreakDay());
        assertEquals(50, result.getBonusPoints());
    }

    @Test
    void signIn_unpublishedConfig_throwsException() {
        SignInConfig config = createConfig(1L, "MONTHLY", 10, null, null, false, 0, null);
        config.setStatus(SignInConfigStatus.DRAFT.name());
        doReturn(config).when(configMapper).selectById(1L);

        assertThrows(BusinessException.class, () -> signInService.signIn(1L, "user1"));
    }

    @Test
    void signIn_nonExistentConfig_throwsException() {
        doReturn(null).when(configMapper).selectById(999L);
        assertThrows(BusinessException.class, () -> signInService.signIn(999L, "user1"));
    }

    @Test
    void catchUp_disabled_returnsFail() {
        SignInConfig config = createConfig(1L, "MONTHLY", 10, null, null, false, 0, null);
        doReturn(config).when(configMapper).selectById(1L);

        SignInResult result = signInService.catchUp(1L, "user1", LocalDate.now().minusDays(1));
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未开启补签"));
    }

    @Test
    void catchUp_notPastDate_returnsFail() {
        SignInConfig config = createConfig(1L, "MONTHLY", 10, null, null, true, 0, 3);
        doReturn(config).when(configMapper).selectById(1L);

        SignInResult result = signInService.catchUp(1L, "user1", LocalDate.now());
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("过去的日期"));
    }

    @Test
    void catchUp_alreadySigned_returnsFail() {
        SignInConfig config = createConfig(1L, "MONTHLY", 10, null, null, true, 0, 3);
        doReturn(config).when(configMapper).selectById(1L);

        SignInRecord existing = new SignInRecord();
        existing.setSigninDate(LocalDate.now().minusDays(1));
        doReturn(existing).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));

        SignInResult result = signInService.catchUp(1L, "user1", LocalDate.now().minusDays(1));
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("已签到"));
    }

    @Test
    void catchUp_insufficientPoints_returnsFail() {
        SignInConfig config = createConfig(1L, "MONTHLY", 10, null, null, true, 50, 3);
        doReturn(config).when(configMapper).selectById(1L);
        doReturn(null).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));

        PointAccount account = new PointAccount();
        account.setBalance(10L);
        doReturn(account).when(pointService).getBalance("user1");

        SignInResult result = signInService.catchUp(1L, "user1", LocalDate.now().minusDays(1));
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("积分不足"));
    }

    @Test
    void getStatus_returnsCorrectStatus() {
        String streakConfig = "{\"maxStreak\":30,\"tiers\":[{\"day\":3,\"bonus\":20},{\"day\":7,\"bonus\":50}]}";
        SignInConfig config = createConfig(1L, "MONTHLY", 10, streakConfig, null, false, 0, null);
        doReturn(config).when(configMapper).selectById(1L);

        SignInRecord today = new SignInRecord();
        today.setSigninDate(LocalDate.now());
        today.setStreakDay(5);
        doReturn(today).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));

        SignInRecord r1 = new SignInRecord();
        r1.setStreakDay(5);
        doReturn(List.of(r1)).when(recordMapper).selectList(any(LambdaQueryWrapper.class));

        PointAccount account = new PointAccount();
        account.setBalance(100L);
        doReturn(account).when(pointService).getBalance("user1");

        SignInStatusVO status = signInService.getStatus(1L, "user1");

        assertTrue(status.isTodaySigned());
        assertEquals(5, status.getCurrentStreak());
        assertEquals(100L, status.getPointBalance());
        assertEquals(7, status.getNextTierDay());
        assertEquals(50, status.getNextTierBonus());
    }

    @Test
    void signIn_weeklyPeriod_usesWeekKey() {
        SignInConfig config = createConfig(1L, "WEEKLY", 10, null, null, false, 0, null);
        doReturn(config).when(configMapper).selectById(1L);
        doReturn(null).when(recordMapper).selectOne(any(LambdaQueryWrapper.class));
        doAnswer(inv -> {
            SignInRecord r = inv.getArgument(0);
            r.setId(200L);
            return 1;
        }).when(recordMapper).insert(any(SignInRecord.class));
        PointAccount account = new PointAccount();
        account.setBalance(0L);
        doReturn(account).when(pointService).getBalance("user1");

        SignInResult result = signInService.signIn(1L, "user1");

        assertTrue(result.isSuccess());
        verify(recordMapper).insert(recordCaptor.capture());
        String periodKey = recordCaptor.getValue().getPeriodKey();
        assertTrue(periodKey.contains("-W"), "WEEKLY periodKey should contain '-W': " + periodKey);
    }
}
