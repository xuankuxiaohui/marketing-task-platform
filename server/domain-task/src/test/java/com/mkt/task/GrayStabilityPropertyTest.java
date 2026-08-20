package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.domain.CrowdMembership;
import com.mkt.task.domain.GrayBucket;
import com.mkt.task.domain.VisibilityEvaluator;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** R13.2: gray hit is stable for a fixed (user, task, config). */
class GrayStabilityPropertyTest {

    @Property(tries = 200)
    void grayHitIsDeterministic(@ForAll("cases") GrayCase grayCase) {
        boolean first = VisibilityEvaluator.grayHit(
                grayCase.gray(), grayCase.taskId(), grayCase.userId(), grayCase.crowds());
        for (int i = 0; i < 100; i++) {
            assertThat(VisibilityEvaluator.grayHit(
                            grayCase.gray(), grayCase.taskId(), grayCase.userId(), grayCase.crowds()))
                    .isEqualTo(first);
        }
        int bucket = GrayBucket.of(grayCase.userId(), grayCase.taskId());
        if ("RATIO".equals(grayCase.gray().type())) {
            if (grayCase.gray().ratio() != null && grayCase.gray().ratio() == 0) {
                assertThat(first).isFalse();
            }
            if (grayCase.gray().ratio() != null && grayCase.gray().ratio() == 100) {
                assertThat(first).isTrue();
            }
        }
        if ("AB".equals(grayCase.gray().type())) {
            String group = bucket % 2 == 0 ? "A" : "B";
            if ("AB".equals(grayCase.gray().abGroup())) {
                assertThat(first).isTrue();
            } else {
                assertThat(first).isEqualTo(group.equals(grayCase.gray().abGroup()));
            }
        }
    }

    @Provide
    Arbitrary<GrayCase> cases() {
        Arbitrary<Long> userId = Arbitraries.longs().between(1, 10_000);
        Arbitrary<Long> taskId = Arbitraries.longs().between(1, 200);
        Arbitrary<TaskGrayCommand> gray = Arbitraries.of("NONE", "RATIO", "AB", "CROWD").flatMap(type -> switch (type) {
            case "RATIO" -> Arbitraries.integers().between(0, 100).map(ratio -> new TaskGrayCommand("RATIO", ratio, null, null, null));
            case "AB" -> Arbitraries.of("A", "B", "AB").map(group -> new TaskGrayCommand("AB", null, group, null, null));
            case "CROWD" -> Arbitraries.longs().between(1, 5).map(id -> new TaskGrayCommand("CROWD", null, null, id, id == 1L ? 2L : null));
            default -> Arbitraries.just(new TaskGrayCommand("NONE", null, null, null, null));
        });
        return userId.flatMap(uid -> taskId.flatMap(tid -> gray.map(g -> new GrayCase(uid, tid, g, new FixedCrowds(Set.of(1L, 3L))))));
    }

    record GrayCase(long userId, long taskId, TaskGrayCommand gray, CrowdMembership crowds) {}

    private static final class FixedCrowds implements CrowdMembership {
        private final Set<Long> packs;

        private FixedCrowds(Set<Long> packs) {
            this.packs = packs;
        }

        @Override
        public boolean contains(long crowdId, long userId) {
            return packs.contains(crowdId);
        }
    }

}
