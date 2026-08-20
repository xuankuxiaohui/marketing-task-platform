package com.mkt.task.response;

import java.util.List;

public record VersionDiffResponse(
        List<DiffEntry> steps,
        List<DiffEntry> transitions,
        List<DiffEntry> filter,
        List<DiffEntry> gray,
        List<DiffEntry> actions) {}
