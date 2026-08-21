package com.mkt.reward;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

/** Portal and internal controllers when portal-app is on the classpath (03 §6.1). */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.portal.PortalApplication")
@ComponentScan(basePackages = {"com.mkt.reward.controller.portal", "com.mkt.reward.controller.internal"})
public class RewardPortalAutoConfiguration {}
