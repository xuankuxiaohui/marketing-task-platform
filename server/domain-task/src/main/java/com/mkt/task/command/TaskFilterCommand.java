package com.mkt.task.command;

import java.util.List;

public record TaskFilterCommand(String expr, List<Long> allowCrowdIds, List<Long> excludeCrowdIds) {}
