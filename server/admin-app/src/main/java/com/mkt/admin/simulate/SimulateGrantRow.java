package com.mkt.admin.simulate;

public record SimulateGrantRow(
        long id,
        long prizeId,
        long userId,
        String grantSource,
        String sourceId,
        String fulfillmentStatus,
        String categoryCode) {}
