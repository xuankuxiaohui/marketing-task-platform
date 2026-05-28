package com.marketing.task.signin.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class StreakTierConfig {
    private Integer maxStreak;
    private List<Tier> tiers;

    @Data
    public static class Tier {
        private Integer day;
        private Integer bonus;
    }
}
