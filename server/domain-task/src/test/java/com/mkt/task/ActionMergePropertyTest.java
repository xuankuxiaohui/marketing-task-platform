package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.domain.ActionMerger;
import com.mkt.task.domain.Platforms;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** R16.1: merge is the first hit on the §4.9 fallback chain; repeated eval is stable. */
class ActionMergePropertyTest {

    private static final List<String> PLATFORMS =
            List.of(Platforms.WEB, Platforms.ANDROID, Platforms.IOS, Platforms.MINIAPP, Platforms.SIMULATOR);
    private static final List<String> TYPES = List.of(ActionMerger.NONE, "LINK", "ROUTE", "SCHEME");

    @Property(tries = 200)
    void mergeEqualsFallbackFirstHitAndIsStable(@ForAll("cases") MergeCase mergeCase) {
        TaskActionCommand first =
                ActionMerger.merge(mergeCase.actions(), mergeCase.stepCode(), mergeCase.platform());
        TaskActionCommand expected = expected(mergeCase.actions(), mergeCase.stepCode(), mergeCase.platform());
        assertThat(first.actionType()).isEqualTo(expected.actionType());
        assertThat(first.platform()).isEqualTo(expected.platform());
        assertThat(ActionMerger.params(first)).isEqualTo(ActionMerger.params(expected));
        assertThat(first.buttonText()).isEqualTo(expected.buttonText());
        for (int i = 0; i < 20; i++) {
            TaskActionCommand again =
                    ActionMerger.merge(mergeCase.actions(), mergeCase.stepCode(), mergeCase.platform());
            assertThat(again.actionType()).isEqualTo(first.actionType());
            assertThat(ActionMerger.params(again)).isEqualTo(ActionMerger.params(first));
        }
        TaskActionCommand illegal = ActionMerger.merge(mergeCase.actions(), mergeCase.stepCode(), "pc");
        TaskActionCommand web = ActionMerger.merge(mergeCase.actions(), mergeCase.stepCode(), Platforms.WEB);
        assertThat(illegal.actionType()).isEqualTo(web.actionType());
        TaskActionCommand missing = ActionMerger.merge(mergeCase.actions(), mergeCase.stepCode(), null);
        assertThat(missing.actionType()).isEqualTo(web.actionType());
    }

    @Provide
    Arbitrary<MergeCase> cases() {
        Arbitrary<String> platform = Arbitraries.of(PLATFORMS);
        Arbitrary<List<TaskActionCommand>> actions = Combinators.combine(
                        optionalAction(ActionMerger.SCOPE_TASK, null),
                        optionalAction(ActionMerger.SCOPE_STEP, "click"))
                .as((task, step) -> {
                    List<TaskActionCommand> list = new ArrayList<>();
                    list.addAll(task);
                    list.addAll(step);
                    return list;
                });
        return Combinators.combine(actions, platform).as(MergeCase::new);
    }

    private static Arbitrary<List<TaskActionCommand>> optionalAction(String scope, String stepCode) {
        Arbitrary<String> type = Arbitraries.of(TYPES);
        Arbitrary<String> platform = Arbitraries.of(PLATFORMS);
        Arbitrary<TaskActionCommand> one = Combinators.combine(platform, type)
                .as((p, t) -> new TaskActionCommand(scope, stepCode, p, t, Map.of("k", t), "go"));
        return Arbitraries.of(true, false).flatMap(present -> present ? one.map(List::of) : Arbitraries.just(List.of()));
    }

    static TaskActionCommand expected(List<TaskActionCommand> actions, String stepCode, String platform) {
        String resolved = Platforms.normalize(platform);
        TaskActionCommand hit = find(actions, ActionMerger.SCOPE_STEP, stepCode, resolved);
        if (hit != null) {
            return hit;
        }
        hit = find(actions, ActionMerger.SCOPE_TASK, null, resolved);
        if (hit != null) {
            return hit;
        }
        if (!Platforms.WEB.equals(resolved)) {
            hit = find(actions, ActionMerger.SCOPE_STEP, stepCode, Platforms.WEB);
            if (hit != null) {
                return hit;
            }
            hit = find(actions, ActionMerger.SCOPE_TASK, null, Platforms.WEB);
            if (hit != null) {
                return hit;
            }
        }
        return ActionMerger.nonePlaceholder(stepCode, resolved);
    }

    private static TaskActionCommand find(
            List<TaskActionCommand> actions, String scope, String stepCode, String platform) {
        for (TaskActionCommand action : actions) {
            if (!scope.equals(action.scope()) || !platform.equals(action.platform())) {
                continue;
            }
            if (ActionMerger.SCOPE_STEP.equals(scope) && !stepCode.equals(action.stepCode())) {
                continue;
            }
            return action;
        }
        return null;
    }

    record MergeCase(List<TaskActionCommand> actions, String platform) {
        String stepCode() {
            return "click";
        }
    }
}
