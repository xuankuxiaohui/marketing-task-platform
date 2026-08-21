package com.mkt.reward.response;

public record PrizeImpactResponse(boolean confirmed, int affectedTaskCount, int inFlightInstanceCount) {}
