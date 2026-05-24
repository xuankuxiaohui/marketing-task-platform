package com.marketing.task.domain.dto;

public record LoginResponse(String token, String userId, String username, String nickname,
                            long expiresIn) {
}
