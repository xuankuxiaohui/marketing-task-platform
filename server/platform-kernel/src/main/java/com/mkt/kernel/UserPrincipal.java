package com.mkt.kernel;

/** Authenticated principal held by {@link UserContext}. */
public record UserPrincipal(Long userId, String loginType, String username) {
}
