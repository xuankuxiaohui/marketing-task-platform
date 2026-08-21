package com.mkt.signin.query;

import com.mkt.kernel.PageQuery;

public record SigninActivityQuery(String code, String name, String status, PageQuery page) {}
