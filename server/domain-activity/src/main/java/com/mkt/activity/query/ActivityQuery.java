package com.mkt.activity.query;

import com.mkt.kernel.PageQuery;

public record ActivityQuery(String code, String name, String status, PageQuery page) {}
