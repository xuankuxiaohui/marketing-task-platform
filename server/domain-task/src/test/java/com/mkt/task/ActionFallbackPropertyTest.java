package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.domain.ActionMerger;
import com.mkt.task.domain.Platforms;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** R16.2: first hit stops; empty snapshot returns NONE placeholder and never throws. */
class ActionFallbackPropertyTest {

    private static final List<String> PLATFORMS =
            List.of(Platforms.WEB, Platforms.ANDROID, Platforms.IOS, Platforms.MINIAPP, Platforms.SIMULATOR);

    @Property(tries = 100)
    void fourSnapshotKindsNeverThrowAndStopAtFirstHit(@ForAll("kinds") SnapshotKind kind) {
        List<TaskActionCommand> actions = kind.actions();
        for (String platform : PLATFORMS) {
            assertThatCode(() -> ActionMerger.merge(actions, "click", platform)).doesNotThrowAnyException();
            TaskActionCommand merged = ActionMerger.merge(actions, "click", platform);
            assertThat(merged).isNotNull();
            TaskActionCommand expected = ActionMergePropertyTest.expected(actions, "click", platform);
            assertThat(merged.actionType()).isEqualTo(expected.actionType());
        }
        TaskActionCommand empty = ActionMerger.merge(List.of(), "click", Platforms.IOS);
        assertThat(empty.actionType()).isEqualTo(ActionMerger.NONE);
    }

    @Provide
    Arbitrary<SnapshotKind> kinds() {
        return Arbitraries.of(
                SnapshotKind.NO_STEP,
                SnapshotKind.NO_TASK,
                SnapshotKind.WEB_ONLY,
                SnapshotKind.EMPTY);
    }

    enum SnapshotKind {
        NO_STEP {
            @Override
            List<TaskActionCommand> actions() {
                return List.of(task(Platforms.ANDROID, "LINK"), task(Platforms.WEB, "ROUTE"));
            }
        },
        NO_TASK {
            @Override
            List<TaskActionCommand> actions() {
                return List.of(step(Platforms.ANDROID, "SCHEME"), step(Platforms.WEB, "LINK"));
            }
        },
        WEB_ONLY {
            @Override
            List<TaskActionCommand> actions() {
                return List.of(step(Platforms.WEB, ActionMerger.NONE), task(Platforms.WEB, "LINK"));
            }
        },
        EMPTY {
            @Override
            List<TaskActionCommand> actions() {
                return List.of();
            }
        };

        abstract List<TaskActionCommand> actions();
    }

    private static TaskActionCommand task(String platform, String type) {
        return new TaskActionCommand(ActionMerger.SCOPE_TASK, null, platform, type, Map.of("url", type), "t");
    }

    private static TaskActionCommand step(String platform, String type) {
        return new TaskActionCommand(ActionMerger.SCOPE_STEP, "click", platform, type, Map.of("url", type), "s");
    }
}
