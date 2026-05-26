package com.marketing.task.domain.dto;

public record LoginResponse(String token, String userId, String username, String nickname,
                            long expiresIn, String province, String role, String tags,
                            String orgId, Integer level, String platform) {

    public LoginResponse(String token, String userId, String username, String nickname, long expiresIn) {
        this(token, userId, username, nickname, expiresIn, null, null, null, null, null, null);
    }
}
