package com.mkt.task.response;

import java.util.List;

public record AdminInstanceDetailResponse(
        AdminInstanceView instance, List<AdminInstanceStepView> steps, List<InstanceEventView> events) {}
