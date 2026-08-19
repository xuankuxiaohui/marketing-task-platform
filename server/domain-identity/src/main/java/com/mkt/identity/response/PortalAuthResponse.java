package com.mkt.identity.response;

public record PortalAuthResponse(String token, long userId, String nickname) {}
