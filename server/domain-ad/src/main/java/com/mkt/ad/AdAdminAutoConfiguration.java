package com.mkt.ad;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
@ComponentScan(basePackages = "com.mkt.ad.controller.admin")
public class AdAdminAutoConfiguration {}
