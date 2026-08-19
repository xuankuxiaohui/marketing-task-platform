package com.mkt.tracking.command;

public record TrackIdentity(Long userId, String deviceId, String ip) {
}
