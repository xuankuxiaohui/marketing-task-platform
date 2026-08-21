package com.mkt.activity.command;

import java.time.Instant;

public record ActivityScheduleCommand(Instant publishAt, Instant offlineAt) {}
