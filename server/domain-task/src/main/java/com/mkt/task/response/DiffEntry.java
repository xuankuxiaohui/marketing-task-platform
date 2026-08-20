package com.mkt.task.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiffEntry(String op, String key, Object left, Object right) {}
