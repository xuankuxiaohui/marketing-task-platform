package com.mkt.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivityPublishCommand;
import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.activity.convert.ActivityOwnership;
import com.mkt.activity.domain.HitRules;
import com.mkt.activity.response.ParticipateResponse;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.activity.support.ActivitySettings;
import com.mkt.activity.testsupport.MemoryActivityStores;
import com.mkt.activity.testsupport.RecordingRewardPort;
import com.mkt.activity.testsupport.StubUserAttributePort;
import com.mkt.contract.CrowdPort;
import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActivityPortalAppServiceTest {

    private static final CrowdPort NO_CROWD = new CrowdPort() {
        @Override
        public boolean memberOf(long crowdId, long userId) {
            return false;
        }

        @Override
        public boolean memberOfCode(String crowdCode, long userId) {
            return false;
        }
    };


    private MemoryActivityStores stores;
    private RecordingRewardPort rewards;
    private ActivityAdminAppService admin;
    private ActivityPortalAppService portal;
    private StubUserAttributePort users;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        stores = new MemoryActivityStores();
        rewards = new RecordingRewardPort();
        users = new StubUserAttributePort();
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC);
        admin = new ActivityAdminAppService(stores.activities, stores.participations, rewards, new ActivitySettings(), clock);
        portal = new ActivityPortalAppService(stores.activities, stores.participations, rewards, users, NO_CROWD, clock);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void participateGrantsOnceAndRespectsQuotaAndEtagHash() {
        long id = publish(10, null);
        UserContext.set(new UserPrincipal(9L, "client", "bob"));
        ParticipateResponse first = portal.participate(id, 9L);
        assertThat(first.result()).isEqualTo(HitRules.PASS);
        assertThat(first.granted()).isTrue();
        assertThat(rewards.uniqueGrants()).hasSize(1);
        assertThat(rewards.uniqueGrants().get(0).source()).isEqualTo(GrantSource.ACTIVITY_PARTICIPATION);
        assertThat(rewards.uniqueGrants().get(0).sourceId()).isEqualTo(String.valueOf(first.participationId()));

        long quotaId = publish(11, 1);
        users.put(21L, "110000", Instant.parse("2026-08-01T00:00:00Z"));
        users.put(22L, "110000", Instant.parse("2026-08-01T00:00:00Z"));
        ParticipateResponse pass = portal.participate(quotaId, 21L);
        ParticipateResponse reject = portal.participate(quotaId, 22L);
        assertThat(pass.result()).isEqualTo(HitRules.PASS);
        assertThat(reject.result()).isEqualTo(HitRules.REJECT);
        assertThat(stores.participations.listByQuery(quotaId, 22L, HitRules.REJECT, null, 0, 10).get(0).getHitRule())
                .isEqualTo(HitRules.GLOBAL_DAILY);

        assertThat(portal.listPublished(9L)).isNotEmpty();
        assertThat(portal.detail(id, 9L).richText()).contains("<p>");
        assertThat(portal.detail(id, 9L).contentHash()).isNotBlank();
    }

    @Test
    void anonymousListShowsPublicAndHidesGrayWithout500() {
        long publicId = publish(10, null);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        long grayId = admin.save(save("grayed", "<p>g</p>", 10, null, new ActivityGrayCommand("RATIO", 50)))
                .id();
        admin.publish(grayId, new ActivityPublishCommand(true, false));
        UserContext.clear();
        assertThat(portal.listPublished(null)).extracting(v -> v.code()).containsExactly("act10a");
        assertThat(portal.detail(publicId, null).code()).isEqualTo("act10a");
        assertThatThrownBy(() -> portal.detail(grayId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ActivityErrorCodes.NOT_FOUND);
    }

    @Test
    void listIncludesTaskSubmodulesAndOwnershipJoinsParticipation() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        long id = admin.save(new ActivitySaveCommand(
                        null,
                        "withtask",
                        "带任务专题",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        "<p>ok</p>",
                        new ActivityGrayCommand("NONE", null),
                        List.of(new ActivitySubmoduleCommand("TASK", 22L, 1)),
                        10L,
                        List.of(),
                        List.of(),
                        false,
                        7,
                        null,
                        null,
                        null,
                        List.of()))
                .id();
        admin.publish(id, new ActivityPublishCommand(true, false));
        UserContext.set(new UserPrincipal(9L, "client", "bob"));
        assertThat(portal.listPublished(9L))
                .anySatisfy(card -> {
                    assertThat(card.id()).isEqualTo(id);
                    assertThat(card.submodules()).hasSize(1);
                    assertThat(card.submodules().get(0).type()).isEqualTo("TASK");
                    assertThat(card.submodules().get(0).refId()).isEqualTo(22L);
                });
        List<ActivityOwnership.Ref> refs = portal.ownershipForTask(22L);
        assertThat(refs).extracting(ActivityOwnership.Ref::activityId).contains(id);
        ParticipateResponse pass = portal.participate(id, 9L);
        ActivityOwnership.Ref owned = portal.ownershipForParticipation(pass.participationId());
        assertThat(owned.activityId()).isEqualTo(id);
        assertThat(owned.activityName()).isEqualTo("带任务专题");
    }

    @Test
    void unpublishedIsNotFoundOnPortal() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        long id = admin.save(save("hidden", "<p>x</p>", 10, null)).id();
        UserContext.set(new UserPrincipal(9L, "client", "bob"));
        assertThatThrownBy(() -> portal.detail(id, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ActivityErrorCodes.NOT_FOUND);
    }

    private long publish(long prizeId, Integer globalDaily) {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        String code = "act" + prizeId + (globalDaily == null ? "a" : "b");
        long id = admin.save(save(code, "<p>ok</p>", prizeId, globalDaily)).id();
        admin.publish(id, new ActivityPublishCommand(true, false));
        return id;
    }

    private static ActivitySaveCommand save(String code, String html, long prizeId, Integer globalDaily) {
        return save(code, html, prizeId, globalDaily, new ActivityGrayCommand("NONE", null));
    }

    private static ActivitySaveCommand save(
            String code, String html, long prizeId, Integer globalDaily, ActivityGrayCommand gray) {
        return new ActivitySaveCommand(
                null,
                code,
                "专题",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T16:00:00Z"),
                html,
                gray,
                List.of(),
                prizeId,
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
