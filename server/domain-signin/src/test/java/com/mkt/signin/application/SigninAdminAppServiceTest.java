package com.mkt.signin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.signin.command.SigninActivitySaveCommand;
import com.mkt.signin.command.SigninPublishCommand;
import com.mkt.signin.command.SigninTierCommand;
import com.mkt.signin.domain.ActivityStatuses;
import com.mkt.signin.query.SigninActivityQuery;
import com.mkt.signin.response.SigninActivitySaveResponse;
import com.mkt.signin.response.SigninPublishResponse;
import com.mkt.signin.support.SigninErrorCodes;
import com.mkt.signin.testsupport.MemorySigninStores;
import com.mkt.signin.testsupport.RecordingRewardPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SigninAdminAppServiceTest {

    private MemorySigninStores stores;
    private SigninAdminAppService admin;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        stores = new MemorySigninStores();
        admin = new SigninAdminAppService(
                stores.activities,
                stores.snapshots,
                stores.records,
                new RecordingRewardPort(),
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void savePublishAndRevision() {
        SigninActivitySaveResponse created = admin.save(new SigninActivitySaveCommand(
                null,
                "daily_check",
                "每日签到",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T16:00:00Z"),
                List.of(new SigninTierCommand(1, 10L))));
        assertThat(created.status()).isEqualTo(ActivityStatuses.DRAFT);
        SigninPublishResponse published = admin.publish(created.id(), new SigninPublishCommand(true, false));
        assertThat(published.version()).isEqualTo(1);
        assertThat(published.status()).isEqualTo(ActivityStatuses.PUBLISHED);
        admin.save(new SigninActivitySaveCommand(
                created.id(),
                "daily_check",
                "每日签到改",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T16:00:00Z"),
                List.of(new SigninTierCommand(1, 10L), new SigninTierCommand(3, 11L))));
        assertThat(admin.get(created.id()).pendingRevision()).isTrue();
        SigninPublishResponse preview = admin.publish(created.id(), new SigninPublishCommand(false, false));
        assertThat(preview.requiresConfirm()).isTrue();
        SigninPublishResponse v2 = admin.publish(created.id(), new SigninPublishCommand(true, false));
        assertThat(v2.version()).isEqualTo(2);
        assertThat(stores.snapshots.listByActivityId(created.id())).hasSize(2);
    }

    @Test
    void duplicateCodeRejected() {
        SigninActivitySaveCommand cmd = new SigninActivitySaveCommand(
                null,
                "daily_check",
                "每日签到",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T16:00:00Z"),
                List.of(new SigninTierCommand(1, 10L)));
        admin.save(cmd);
        assertThatThrownBy(() -> admin.save(cmd))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SigninErrorCodes.CODE_DUPLICATE);
    }

    @Test
    void publishedCannotDelete() {
        long id = admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L))))
                .id();
        admin.publish(id, new SigninPublishCommand(true, false));
        assertThatThrownBy(() -> admin.delete(id))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SigninErrorCodes.PUBLISHED_NOT_DELETABLE);
    }

    @Test
    void scheduleThenOffline() {
        long id = admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L))))
                .id();
        admin.schedule(id, new com.mkt.signin.command.SigninScheduleCommand(Instant.parse("2026-08-21T00:00:00Z")));
        assertThat(admin.get(id).status()).isEqualTo(ActivityStatuses.SCHEDULED);
        admin.publish(id, new SigninPublishCommand(true, true));
        assertThat(admin.get(id).status()).isEqualTo(ActivityStatuses.PUBLISHED);
        admin.offline(id);
        assertThat(admin.get(id).status()).isEqualTo(ActivityStatuses.OFFLINE);
    }

    @Test
    void pageListsDraft() {
        admin.save(new SigninActivitySaveCommand(
                null,
                "daily_check",
                "每日签到",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T16:00:00Z"),
                List.of(new SigninTierCommand(1, 10L))));
        assertThat(admin.page(new SigninActivityQuery(null, null, null, PageQuery.of(1, 20))).total())
                .isEqualTo(1);
    }

    @Test
    void rejectsInvalidCodeWindowTiersAndPrize() {
        assertThatThrownBy(() -> admin.save(new SigninActivitySaveCommand(
                        null,
                        "BAD",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L)))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-31T16:00:00Z"),
                        Instant.parse("2026-08-01T00:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L)))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SigninErrorCodes.TIME_WINDOW_INVALID);
        assertThatThrownBy(() -> admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SigninErrorCodes.TIER_INVALID);
        long id = admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 99L))))
                .id();
        assertThatThrownBy(() -> admin.publish(id, new SigninPublishCommand(true, false)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SigninErrorCodes.PRIZE_INVALID);
    }

    @Test
    void offlineCanBeReeditedAndRecordsPage() {
        long id = admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L))))
                .id();
        admin.publish(id, new SigninPublishCommand(true, false));
        admin.offline(id);
        admin.save(new SigninActivitySaveCommand(
                id,
                "daily_check",
                "再编",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T16:00:00Z"),
                List.of(new SigninTierCommand(1, 10L))));
        assertThat(admin.get(id).status()).isEqualTo(ActivityStatuses.DRAFT);
        assertThat(admin.pageRecords(new com.mkt.signin.query.SigninRecordQuery(
                        id, 9L, java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 8, 31), PageQuery.of(1, 20)))
                .total())
                .isZero();
        assertThat(admin.publishDue(10)).isZero();
    }

    @Test
    void draftDeleteAndCodeLocked() {
        long id = admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L))))
                .id();
        assertThatThrownBy(() -> admin.save(new SigninActivitySaveCommand(
                        id,
                        "other_code",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L)))))
                .isInstanceOf(BusinessException.class);
        assertThat(admin.delete(id).ok()).isTrue();
        assertThatThrownBy(() -> admin.get(id)).isInstanceOf(BusinessException.class);
    }
}
