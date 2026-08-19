package com.mkt.identity.config;

/** RL-11: the only reader of sys_config. */
public interface ConfigService {

    int getInt(String key, int defaultValue);
}
