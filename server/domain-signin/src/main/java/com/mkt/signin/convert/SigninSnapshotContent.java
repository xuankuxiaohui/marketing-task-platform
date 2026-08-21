package com.mkt.signin.convert;

import com.mkt.signin.command.SigninTierCommand;
import java.time.Instant;
import java.util.List;

public record SigninSnapshotContent(
        String code, String name, Instant startTime, Instant endTime, List<SigninTierCommand> tiers) {}
