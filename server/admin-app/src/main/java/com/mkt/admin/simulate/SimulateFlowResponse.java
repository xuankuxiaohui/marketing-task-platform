package com.mkt.admin.simulate;

import java.util.List;

public record SimulateFlowResponse(
        long instanceId, String instanceStatus, List<SimulateFlowStepView> steps, List<Long> grantRecordIds) {}
