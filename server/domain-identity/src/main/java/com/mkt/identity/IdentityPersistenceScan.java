package com.mkt.identity;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(
        basePackages = {"com.mkt.identity.mapper", "com.mkt.identity.config"},
        annotationClass = Mapper.class)
@ComponentScan(basePackages = {"com.mkt.identity.application", "com.mkt.identity.config"})
public class IdentityPersistenceScan {}
