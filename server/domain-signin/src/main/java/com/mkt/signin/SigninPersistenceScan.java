package com.mkt.signin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.mkt.signin.mapper")
@ComponentScan(basePackages = {"com.mkt.signin.application", "com.mkt.signin.support"})
public class SigninPersistenceScan {}
