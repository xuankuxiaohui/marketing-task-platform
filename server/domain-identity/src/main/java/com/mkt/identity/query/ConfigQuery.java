package com.mkt.identity.query;

import com.mkt.kernel.PageQuery;

public record ConfigQuery(String configGroup, String key, PageQuery page) {}
