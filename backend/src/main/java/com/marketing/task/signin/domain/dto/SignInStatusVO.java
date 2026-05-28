package com.marketing.task.signin.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignInStatusVO {
    private boolean todaySigned;
    private Integer currentStreak;
    private Integer totalSignedDays;
    private Long pointBalance;
    private Integer nextTierDay;
    private Integer nextTierBonus;
}
