package com.mkt.activity.convert;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.activity.command.ActivitySubmoduleCommand;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityOwnershipTest {

    @Test
    void mapsTaskSubmodulesDeterministicallyAndJoinsNames() {
        List<ActivityOwnership.Module> modules = List.of(
                new ActivityOwnership.Module(
                        20L, "夏日", List.of(new ActivitySubmoduleCommand("TASK", 8L, 1))),
                new ActivityOwnership.Module(
                        7L, "春日", List.of(new ActivitySubmoduleCommand("TASK", 8L, 0))),
                new ActivityOwnership.Module(
                        9L, "其它", List.of(new ActivitySubmoduleCommand("SIGNIN", 8L, 0))));
        List<ActivityOwnership.Ref> refs = ActivityOwnership.forTask(8L, modules);
        assertThat(refs).extracting(ActivityOwnership.Ref::activityId).containsExactly(7L, 20L);
        assertThat(ActivityOwnership.first(refs).activityId()).isEqualTo(7L);
        assertThat(ActivityOwnership.joinedNames(refs)).isEqualTo("春日、夏日");
        assertThat(ActivityOwnership.forTask(99L, modules)).isEmpty();
        assertThat(ActivityOwnership.first(List.of())).isNull();
        assertThat(ActivityOwnership.joinedNames(List.of())).isNull();
    }
}
