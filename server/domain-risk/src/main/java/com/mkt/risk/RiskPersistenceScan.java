package com.mkt.risk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.mkt.risk.mapper")
@ComponentScan(basePackages = {"com.mkt.risk.application", "com.mkt.risk.support"})
public class RiskPersistenceScan {}
