package com.mkt.activity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.mkt.activity.mapper")
@ComponentScan(basePackages = {"com.mkt.activity.application", "com.mkt.activity.support"})
public class ActivityPersistenceScan {}
