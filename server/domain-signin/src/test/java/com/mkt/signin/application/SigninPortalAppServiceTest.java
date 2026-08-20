package com.mkt.signin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.signin.command.SigninActivitySaveCommand;
import com.mkt.signin.command.SigninPublishCommand;
import com.mkt.signin.command.SigninTierCommand;
import com.mkt.signin.domain.CalendarCellState;
import com.mkt.signin.response.SigninActionResponse;
import com.mkt.signin.response.SigninCalendarResponse;
import com.mkt.signin.support.SigninErrorCodes;
import com.mkt.signin.support.SigninSettings;
import com.mkt.signin.testsupport.MemorySigninStores;
import com.mkt.signin.testsupport.RecordingRewardPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SigninPortalAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T04:00:00Z"); // UTC+8 12:00
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    private MemorySigninStores stores;
    private RecordingRewardPort rewards;
    private SigninSettings settings;
    private SigninAdminAppService admin;
    private SigninPortalAppService portal;
    private long activityId;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        stores = new MemorySigninStores();
        rewards = new RecordingRewardPort();
        settings = new SigninSettings();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        admin = new SigninAdminAppService(stores.activities, stores.snapshots, stores.records, rewards, clock);
        portal = new SigninPortalAppService(stores.activities, stores.snapshots, stores.records, rewards, settings, clock);
        activityId = admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L), new SigninTierCommand(3, 11L))))
                .id();
        admin.publish(activityId, new SigninPublishCommand(true, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void checkinIsIdempotentAndGrantsDayOneOnce() {
        SigninActionResponse first = portal.checkin(activityId, 9L);
        SigninActionResponse second = portal.checkin(activityId, 9L);
        assertThat(first.alreadySigned()).isFalse();
        assertThat(first.consecutiveDays()).isEqualTo(1);
        assertThat(first.rewards()).hasSize(1);
        assertThat(first.rewards().get(0).day()).isEqualTo(1);
        assertThat(second.alreadySigned()).isTrue();
        assertThat(second.message()).isEqualTo(SigninErrorCodes.DUPLICATE_DAY.message());
        assertThat(stores.records.all()).hasSize(1);
        assertThat(rewards.uniqueGrants()).hasSize(1);
        assertThat(rewards.uniqueGrants().get(0).source()).isEqualTo(GrantSource.SIGNIN_DAY);
        assertThat(rewards.uniqueGrants().get(0).sourceId()).isEqualTo(activityId + ":9:1");
    }

    @Test
    void catchupConsumesThenWritesAndRestoresStreak() {
        portal.checkin(activityId, 9L);
        SigninActionResponse catchup = portal.catchup(activityId, 9L, "2026-08-19");
        assertThat(catchup.alreadySigned()).isFalse();
        assertThat(catchup.source()).isEqualTo("CATCHUP");
        assertThat(rewards.consumes).hasSize(1);
        assertThat(rewards.consumes.get(0).points()).isEqualTo(100);
        assertThat(catchup.consecutiveDays()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void catchupPropagatesConsumeFailure() {
        rewards.consumeFail = new BusinessException(SigninErrorCodes.CATCHUP_NOT_ALLOWED, "积分余额不足");
        assertThatThrownBy(() -> portal.catchup(activityId, 9L, "2026-08-19"))
                .isInstanceOf(BusinessException.class);
        assertThat(rewards.grants).isEmpty();
        assertThat(rewards.consumes).isEmpty();
    }

    @Test
    void catchupOutsideWindowRejected() {
        assertThatThrownBy(() -> portal.catchup(activityId, 9L, "2026-08-01"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SigninErrorCodes.CATCHUP_WINDOW);
    }

    @Test
    void calendarFourStates() {
        portal.checkin(activityId, 9L);
        portal.catchup(activityId, 9L, "2026-08-19");
        SigninCalendarResponse cal = portal.calendar(activityId, 9L, "2026-08");
        assertThat(cal.days().stream().filter(d -> d.date().equals(TODAY)).findFirst())
                .get()
                .extracting(d -> d.state())
                .isEqualTo(CalendarCellState.SIGNED.name());
        assertThat(cal.days().stream().filter(d -> d.date().equals(LocalDate.of(2026, 8, 19))).findFirst())
                .get()
                .extracting(d -> d.state())
                .isEqualTo(CalendarCellState.CATCHUP.name());
        assertThat(cal.consecutiveDays()).isGreaterThanOrEqualTo(2);
        assertThat(cal.catchupCostPoints()).isEqualTo(100);
    }

    @Test
    void listPublishedAndRejectsUnpublished() {
        assertThat(portal.listPublished(NOW)).isNotEmpty();
        long draft = admin.save(new SigninActivitySaveCommand(
                        null,
                        "other_act",
                        "草稿",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L))))
                .id();
        assertThatThrownBy(() -> portal.calendar(draft, 9L, "2026-08"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SigninErrorCodes.NOT_PUBLISHED);
        assertThatThrownBy(() -> portal.calendar(999L, 9L, "2026-08")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> portal.calendar(activityId, 9L, "not-a-month"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void catchupDailyLimitAndNotAllowed() {
        settings.setDailyLimit(1);
        portal.catchup(activityId, 9L, "2026-08-19");
        assertThatThrownBy(() -> portal.catchup(activityId, 9L, "2026-08-18"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SigninErrorCodes.CATCHUP_LIMIT);
        assertThatThrownBy(() -> portal.catchup(activityId, 9L, TODAY.toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SigninErrorCodes.CATCHUP_NOT_ALLOWED);
        assertThatThrownBy(() -> portal.catchup(activityId, 9L, "bad"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void restreakDoesNotRegrantSameTier() {
        portal.checkin(activityId, 9L);
        int first = rewards.uniqueGrants().size();
        Clock later = Clock.fixed(Instant.parse("2026-08-22T04:00:00Z"), ZoneOffset.UTC);
        SigninPortalAppService day3 = new SigninPortalAppService(
                stores.activities, stores.snapshots, stores.records, rewards, settings, later);
        day3.checkin(activityId, 9L);
        assertThat(rewards.uniqueGrants()).hasSize(first);
    }
}
