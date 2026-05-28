package com.marketing.task.activity.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivitySubModulesVO {
    private List<TaskSummary> tasks;
    private List<SignInConfigSummary> signInConfigs;
    private List<PrizeSummary> prizes;

    @Data
    public static class TaskSummary {
        private Long id;
        private String name;
        private String status;
    }

    @Data
    public static class SignInConfigSummary {
        private Long id;
        private String name;
        private String status;
    }

    @Data
    public static class PrizeSummary {
        private Long id;
        private String name;
        private String type;
    }
}
