package com.mkt.reward.query;

import com.mkt.kernel.PageQuery;

public record PrizeQuery(
        String code, String name, String categoryCode, String status, Long groupId, PageQuery page) {}
