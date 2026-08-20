package com.mkt.reward;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

/** Admin controllers only when admin-app is on the classpath (03 §6.1). */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
@ComponentScan(basePackages = "com.mkt.reward.controller.admin")
public class RewardAdminAutoConfiguration {}
