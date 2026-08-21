package com.mkt.ad.query;

import com.mkt.kernel.PageQuery;

public record AdPositionQuery(String code, String form, String status, PageQuery page) {}
