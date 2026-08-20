package com.mkt.task.query;

import com.mkt.kernel.PageQuery;
import java.time.Instant;

public record InstanceQuery(
        Long taskId,
        Long userId,
        String status,
        Integer simulated,
        Instant from,
        Instant to,
        PageQuery page) {}
