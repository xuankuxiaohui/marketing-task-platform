package com.mkt.identity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({"com.mkt.identity.mapper", "com.mkt.identity.config"})
@ComponentScan(basePackages = {"com.mkt.identity.application", "com.mkt.identity.config"})
public class IdentityPersistenceScan {}
