package com.mkt.tracking.domain;

/** Attribute dictionary entry persisted in {@code evt_event_metadata.prop_schema} (§4.7). */
public record PropSchemaItem(String name, String type, boolean required, String remark) {}
