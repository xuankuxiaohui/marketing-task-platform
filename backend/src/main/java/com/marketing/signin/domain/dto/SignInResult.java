package com.marketing.signin.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignInResult {
    private boolean success;
    private Long recordId;
    private Integer streakDay;
    private Integer basePoints;
    private Integer bonusPoints;
    private Integer totalPoints;
    private Integer tierReached;
    private Long pointBalance;
    private boolean catchUp;
    private String message;

    public static SignInResult ok(Long recordId, int streakDay, int basePoints, int bonusPoints,
                                  int totalPoints, Integer tierReached, long pointBalance, boolean catchUp) {
        return new SignInResult(true, recordId, streakDay, basePoints, bonusPoints,
                totalPoints, tierReached, pointBalance, catchUp, null);
    }

    public static SignInResult fail(String message) {
        return new SignInResult(false, null, 0, 0, 0, 0, null, 0L, false, message);
    }
}
