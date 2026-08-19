package com.mkt.identity.response;

import java.time.Instant;

public record RoleView(long id, String code, String name, String status, long userCount, Instant createdAt) {}
