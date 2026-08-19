package com.mkt.tracking.response;

import java.util.Map;

public record TrackDebugEventItem(String code, Map<String, Object> props, String clientTime) {}
