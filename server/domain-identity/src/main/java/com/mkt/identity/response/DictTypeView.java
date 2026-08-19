package com.mkt.identity.response;

import java.time.Instant;

public record DictTypeView(long id, String code, String name, String status, String remark, Instant createdAt) {}
