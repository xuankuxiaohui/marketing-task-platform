package com.mkt.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivityPublishCommand;
import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.command.ActivityScheduleCommand;
import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.activity.domain.ActivityStatuses;
import com.mkt.activity.domain.HitRules;
import com.mkt.activity.query.ActivityQuery;
import com.mkt.activity.query.ParticipationQuery;
import com.mkt.activity.response.ActivityPublishResponse;
import com.mkt.activity.response.ActivitySaveResponse;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.activity.support.ActivitySettings;
import com.mkt.activity.testsupport.MemoryActivityStores;
import com.mkt.activity.testsupport.RecordingRewardPort;
import com.mkt.activity.testsupport.StubUserAttributePort;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActivityAdminAppServiceTest {

    private MemoryActivityStores stores;
    private RecordingRewardPort rewards;
    private ActivityAdminAppService admin;
    private ActivityPortalAppService portal;
    private Clock clock;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        stores = new MemoryActivityStores();
        rewards = new RecordingRewardPort();
        clock = Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC);
        ActivitySettings settings = new ActivitySettings();
        admin = new ActivityAdminAppService(stores.activities, stores.participations, rewards, settings, clock);
        portal = new ActivityPortalAppService(
                stores.activities, stores.participations, rewards, new StubUserAttributePort(), clock);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void savePublishScheduleOfflineAndRejectHtml() {
        ActivitySaveResponse created = admin.save(sample(null, "<p>hello</p><script>x</script>"));
        assertThat(created.status()).isEqualTo(ActivityStatuses.DRAFT);
        assertThat(admin.get(created.id()).richText()).contains("<p>").doesNotContain("<script");

        admin.schedule(created.id(), new ActivityScheduleCommand(Instant.parse("2026-08-21T00:00:00Z"), null));
        assertThat(admin.get(created.id()).status()).isEqualTo(ActivityStatuses.SCHEDULED);

        ActivityPublishResponse published = admin.publish(created.id(), new ActivityPublishCommand(true, true));
        assertThat(published.status()).isEqualTo(ActivityStatuses.PUBLISHED);
        assertThat(published.version()).isEqualTo(1);

        admin.save(sample(created.id(), "<p>v2</p>"));
        ActivityPublishResponse preview = admin.publish(created.id(), new ActivityPublishCommand(false, false));
        assertThat(preview.requiresConfirm()).isTrue();
        admin.publish(created.id(), new ActivityPublishCommand(true, false));
        assertThat(admin.get(created.id()).version()).isEqualTo(2);

        assertThat(admin.page(new ActivityQuery("summer", null, null, PageQuery.of(1, 20))).total()).isEqualTo(1);
        admin.offline(created.id());
        assertThat(admin.get(created.id()).status()).isEqualTo(ActivityStatuses.OFFLINE);

        assertThatThrownBy(() -> admin.save(sample(null, "<script>alert(1)</script>")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ActivityErrorCodes.HTML_EMPTY);
    }

    @Test
    void participationQueryAndStats() {
        long id = admin.save(sample(null, "<p>ok</p>", 10)).id();
        admin.publish(id, new ActivityPublishCommand(true, false));
        UserContext.set(new UserPrincipal(9L, "client", "bob"));
        portal.participate(id, 9L);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        assertThat(admin.pageParticipations(new ParticipationQuery(id, 9L, HitRules.PASS, null, PageQuery.of(1, 20)))
                        .total())
                .isEqualTo(1);
        assertThat(admin.stats(id).passCount()).isEqualTo(1);
        assertThat(admin.stats(id).passRate()).isEqualTo(1.0d);
    }

    @Test
    void publishDueAndOfflineDue() {
        long id = admin.save(sample(null, "<p>ok</p>")).id();
        admin.schedule(
                id,
                new ActivityScheduleCommand(Instant.parse("2026-08-21T00:00:00Z"), Instant.parse("2026-08-22T00:00:00Z")));
        Clock later = Clock.fixed(Instant.parse("2026-08-21T01:00:00Z"), ZoneOffset.UTC);
        ActivityAdminAppService laterAdmin =
                new ActivityAdminAppService(stores.activities, stores.participations, rewards, new ActivitySettings(), later);
        assertThat(laterAdmin.publishDue(10)).isEqualTo(1);
        assertThat(laterAdmin.offlineDue(10)).isZero();
        Clock afterOffline = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
        ActivityAdminAppService offAdmin = new ActivityAdminAppService(
                stores.activities, stores.participations, rewards, new ActivitySettings(), afterOffline);
        assertThat(offAdmin.offlineDue(10)).isEqualTo(1);
    }

    private static ActivitySaveCommand sample(Long id, String html) {
        return sample(id, html, null);
    }

    private static ActivitySaveCommand sample(Long id, String html, Integer globalDaily) {
        return new ActivitySaveCommand(
                id,
                "summer",
                "夏季专题",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T16:00:00Z"),
                html,
                new ActivityGrayCommand("NONE", null),
                List.of(new ActivitySubmoduleCommand("TASK", 3L, 1)),
                10L,
                List.of(),
                List.of(),
                false,
                7,
                null,
                null,
                globalDaily,
                List.of());
    }
}
