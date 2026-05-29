package com.marketing.signin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.common.BusinessException;
import com.marketing.signin.domain.entity.PointAccount;
import com.marketing.signin.domain.entity.PointTransaction;
import com.marketing.signin.domain.enums.PointTransactionStatus;
import com.marketing.signin.domain.enums.PointTransactionType;
import com.marketing.signin.mapper.PointAccountMapper;
import com.marketing.signin.mapper.PointTransactionMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private PointAccountMapper accountMapper;
    @Mock
    private PointTransactionMapper transactionMapper;

    @Captor
    private ArgumentCaptor<PointAccount> accountCaptor;
    @Captor
    private ArgumentCaptor<PointTransaction> txCaptor;

    private PointService pointService;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, PointAccount.class);
        TableInfoHelper.initTableInfo(assistant, PointTransaction.class);
    }

    @BeforeEach
    void setUp() {
        pointService = new PointService(accountMapper, transactionMapper);
    }

    @Test
    void earn_createsTransactionAndUpdatesBalance() {
        doReturn(null).when(accountMapper).selectOne(any(LambdaQueryWrapper.class));
        doAnswer(invocation -> {
            PointAccount a = invocation.getArgument(0);
            a.setId(1L);
            return 1;
        }).when(accountMapper).insert(any(PointAccount.class));
        doReturn(1).when(accountMapper).updateById(any(PointAccount.class));
        doReturn(1).when(transactionMapper).insert(any(PointTransaction.class));

        pointService.earn("user1", 100L, "SIGNIN", 99L,
                LocalDateTime.now().plusDays(30), "每日签到");

        verify(accountMapper).insert(accountCaptor.capture());
        PointAccount created = accountCaptor.getValue();
        assertEquals("user1", created.getUserId());
        assertEquals(100L, created.getBalance());
        assertEquals(100L, created.getTotalEarned());

        verify(transactionMapper).insert(txCaptor.capture());
        PointTransaction tx = txCaptor.getValue();
        assertEquals("user1", tx.getUserId());
        assertEquals(PointTransactionType.EARN.name(), tx.getType());
        assertEquals(100L, tx.getAmount());
        assertEquals("SIGNIN", tx.getSourceType());
        assertEquals(PointTransactionStatus.ACTIVE.name(), tx.getStatus());
    }

    @Test
    void earn_existingAccount_addsToBalance() {
        PointAccount existing = new PointAccount();
        existing.setId(1L);
        existing.setUserId("user1");
        existing.setBalance(50L);
        existing.setTotalEarned(50L);
        existing.setTotalSpent(0L);
        existing.setTotalExpired(0L);

        doReturn(existing).when(accountMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(accountMapper).updateById(any(PointAccount.class));
        doReturn(1).when(transactionMapper).insert(any(PointTransaction.class));

        pointService.earn("user1", 30L, "SIGNIN", 100L, null, "签到");

        verify(accountMapper).updateById(accountCaptor.capture());
        assertEquals(80L, accountCaptor.getValue().getBalance());
        assertEquals(80L, accountCaptor.getValue().getTotalEarned());
    }

    @Test
    void earn_zeroAmount_throwsException() {
        assertThrows(BusinessException.class, () ->
                pointService.earn("user1", 0, "SIGNIN", 1L, null, "test"));
    }

    @Test
    void earn_negativeAmount_throwsException() {
        assertThrows(BusinessException.class, () ->
                pointService.earn("user1", -10, "SIGNIN", 1L, null, "test"));
    }

    @Test
    void deduct_sufficientBalance_succeeds() {
        PointAccount existing = new PointAccount();
        existing.setId(1L);
        existing.setUserId("user1");
        existing.setBalance(100L);
        existing.setTotalEarned(100L);
        existing.setTotalSpent(0L);
        existing.setTotalExpired(0L);

        doReturn(existing).when(accountMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(accountMapper).updateById(any(PointAccount.class));
        doReturn(1).when(transactionMapper).insert(any(PointTransaction.class));

        pointService.deduct("user1", 40L, "CATCH_UP", 1L, "补签");

        verify(accountMapper).updateById(accountCaptor.capture());
        assertEquals(60L, accountCaptor.getValue().getBalance());
        assertEquals(40L, accountCaptor.getValue().getTotalSpent());
    }

    @Test
    void deduct_insufficientBalance_throwsException() {
        PointAccount existing = new PointAccount();
        existing.setId(1L);
        existing.setUserId("user1");
        existing.setBalance(10L);
        existing.setTotalEarned(10L);
        existing.setTotalSpent(0L);
        existing.setTotalExpired(0L);

        doReturn(existing).when(accountMapper).selectOne(any(LambdaQueryWrapper.class));

        assertThrows(BusinessException.class, () ->
                pointService.deduct("user1", 50L, "CATCH_UP", 1L, "补签"));
    }

    @Test
    void expire_activeTransaction_deductsFromBalance() {
        PointAccount account = new PointAccount();
        account.setId(1L);
        account.setUserId("user1");
        account.setBalance(100L);
        account.setTotalEarned(100L);
        account.setTotalSpent(0L);
        account.setTotalExpired(0L);

        PointTransaction original = new PointTransaction();
        original.setId(10L);
        original.setUserId("user1");
        original.setType(PointTransactionType.EARN.name());
        original.setAmount(50L);
        original.setStatus(PointTransactionStatus.ACTIVE.name());

        doReturn(original).when(transactionMapper).selectById(10L);
        doReturn(account).when(accountMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(accountMapper).updateById(any(PointAccount.class));
        doReturn(1).when(transactionMapper).updateById(any(PointTransaction.class));
        doReturn(1).when(transactionMapper).insert(any(PointTransaction.class));

        pointService.expire(10L);

        verify(transactionMapper, atLeastOnce()).updateById(txCaptor.capture());
        assertEquals(PointTransactionStatus.EXPIRED.name(), txCaptor.getValue().getStatus());

        verify(accountMapper).updateById(accountCaptor.capture());
        assertEquals(50L, accountCaptor.getValue().getBalance());
        assertEquals(50L, accountCaptor.getValue().getTotalExpired());
    }

    @Test
    void expire_alreadyExpired_skips() {
        PointTransaction original = new PointTransaction();
        original.setId(10L);
        original.setStatus(PointTransactionStatus.EXPIRED.name());

        doReturn(original).when(transactionMapper).selectById(10L);

        pointService.expire(10L);

        verify(accountMapper, never()).selectOne(any());
        verify(transactionMapper, never()).updateById(any(PointTransaction.class));
    }

    @Test
    void getBalance_createsAccountIfNotExist() {
        doReturn(null).when(accountMapper).selectOne(any(LambdaQueryWrapper.class));
        doAnswer(invocation -> {
            PointAccount a = invocation.getArgument(0);
            a.setId(1L);
            return 1;
        }).when(accountMapper).insert(any(PointAccount.class));

        PointAccount result = pointService.getBalance("newuser");

        assertEquals("newuser", result.getUserId());
        assertEquals(0L, result.getBalance());
    }
}
