package com.mkt.identity.command;

public record CacheEvictCommand(String level, String namespace, String prefix, String key) {}
