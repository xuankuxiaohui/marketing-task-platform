package com.mkt.task.response;

import java.util.Map;

public record PlatformActionView(String actionType, Map<String, Object> params, String buttonText) {}
