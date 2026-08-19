package com.mkt.tracking.command;

import java.util.List;

public record TrackBatchCommand(List<TrackEventCommand> events, String platform, String appVersion) {
}
