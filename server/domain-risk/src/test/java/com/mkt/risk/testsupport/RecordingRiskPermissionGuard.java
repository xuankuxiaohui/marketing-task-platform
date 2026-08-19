package com.mkt.risk.testsupport;

import com.mkt.risk.support.RiskPermissionGuard;
import java.util.ArrayList;
import java.util.List;

public final class RecordingRiskPermissionGuard implements RiskPermissionGuard {

    public final List<String> required = new ArrayList<>();

    @Override
    public void require(String permission) {
        required.add(permission);
    }
}
