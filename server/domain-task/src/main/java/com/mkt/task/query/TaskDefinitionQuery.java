package com.mkt.task.query;

import com.mkt.kernel.PageQuery;

public record TaskDefinitionQuery(String code, String name, String status, String category, PageQuery page) {}
