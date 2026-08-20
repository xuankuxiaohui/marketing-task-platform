package com.mkt.task.command;

public record TaskGrayCommand(
        String type, Integer ratio, String abGroup, Long crowdId, Long excludeCrowdId) {}
