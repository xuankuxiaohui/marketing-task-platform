package com.mkt.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.command.TaskActionCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionMergerTest {

    @Test
    void stepPlatformWinsThenNoneStopsFallback() {
        TaskActionCommand none =
                new TaskActionCommand("STEP", "click", "IOS", ActionMerger.NONE, Map.of(), "占位");
        TaskActionCommand web =
                new TaskActionCommand("TASK", null, "WEB", "LINK", Map.of("url", "https://x"), "去看看");
        TaskActionCommand merged = ActionMerger.merge(List.of(web, none), "click", "IOS");
        assertThat(merged.actionType()).isEqualTo(ActionMerger.NONE);
        assertThat(ActionMerger.merge(List.of(web), "click", "IOS").actionType()).isEqualTo("LINK");
    }
}
