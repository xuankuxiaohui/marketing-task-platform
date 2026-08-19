package com.mkt.tracking;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.mkt.tracking.mapper")
@ComponentScan(basePackages = "com.mkt.tracking.application")
public class TrackingPersistenceScan {}
