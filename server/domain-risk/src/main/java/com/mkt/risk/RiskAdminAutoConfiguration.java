package com.mkt.risk;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

/**
 * Admin controllers only when admin-app is on the classpath (03 §6.1). Portal can assemble
 * domain-risk for {@code RiskCheckPort} / {@code RISK_CNT} without exposing {@code /admin/risk/**}.
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
@ComponentScan(basePackages = "com.mkt.risk.controller.admin")
public class RiskAdminAutoConfiguration {}
