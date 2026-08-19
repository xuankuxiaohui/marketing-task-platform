package com.mkt.task.response;

public record CrowdImportResponse(int imported, int deduplicated, int invalid) {}
