package com.mkt.task.response;

import java.util.List;
import java.util.Map;

public record VersionDiffResponse(
        List<DiffEntry> steps,
        List<DiffEntry> transitions,
        Map<String, DiffEntry> filter,
        Map<String, DiffEntry> gray,
        List<DiffEntry> actions) {}
