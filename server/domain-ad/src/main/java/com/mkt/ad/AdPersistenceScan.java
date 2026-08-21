package com.mkt.ad;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.mkt.ad.mapper")
@ComponentScan(basePackages = {"com.mkt.ad.application", "com.mkt.ad.support"})
public class AdPersistenceScan {}
