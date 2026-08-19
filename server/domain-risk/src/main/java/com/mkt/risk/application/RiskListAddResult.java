package com.mkt.risk.application;

import com.mkt.risk.response.RiskListItemResponse;

public record RiskListAddResult(RiskListItemResponse item, boolean duplicate) {
}
