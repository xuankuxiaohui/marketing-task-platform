package com.mkt.tracking.command;

public record TrackEventCommand(String code, Object props, String clientTime) {
}
