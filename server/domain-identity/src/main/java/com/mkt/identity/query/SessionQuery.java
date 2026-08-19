package com.mkt.identity.query;

import com.mkt.kernel.PageQuery;

public record SessionQuery(String accountType, String account, PageQuery page) {}
