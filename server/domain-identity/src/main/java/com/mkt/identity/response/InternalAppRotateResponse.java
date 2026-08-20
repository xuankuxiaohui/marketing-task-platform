package com.mkt.identity.response;

import java.time.Instant;

public record InternalAppRotateResponse(String secret, Instant prevExpireAt) {}
