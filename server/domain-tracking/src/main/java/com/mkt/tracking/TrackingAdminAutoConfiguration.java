package com.mkt.tracking;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

/** Admin controllers only on admin-app (03 §6.1 / design §6.7). */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
@ComponentScan(basePackages = "com.mkt.tracking.controller.admin")
public class TrackingAdminAutoConfiguration {}
