package com.mkt.risk.application;

/** Unique key of {@code risk_list_item} (dimension, list_type, list_value). */
public record RiskListUk(String dimension, String listType, String listValue) {}
