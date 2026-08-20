package com.mkt.identity.response;

import java.time.Instant;

public record InternalAppView(long id, String appId, String appName, String status, Instant prevExpireAt, Instant createdAt) {}
