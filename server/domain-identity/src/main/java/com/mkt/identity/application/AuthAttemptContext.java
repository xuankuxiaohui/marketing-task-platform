package com.mkt.identity.application;

public record AuthAttemptContext(String ip, String userAgent, String deviceId) {}
