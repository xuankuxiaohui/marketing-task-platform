package com.mkt.reward;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.mkt.reward.mapper")
@ComponentScan(basePackages = "com.mkt.reward.application")
public class RewardPersistenceScan {}
