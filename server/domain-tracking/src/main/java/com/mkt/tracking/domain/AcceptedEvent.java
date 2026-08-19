package com.mkt.tracking.domain;

import java.util.Map;

public record AcceptedEvent(String code, Map<String, Object> props, String clientTime, boolean registered) {
}
