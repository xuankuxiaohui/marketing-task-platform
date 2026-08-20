package com.mkt.task;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.mkt.task.mapper")
@ComponentScan(basePackages = {"com.mkt.task.application", "com.mkt.task.support"})
public class TaskPersistenceScan {}
