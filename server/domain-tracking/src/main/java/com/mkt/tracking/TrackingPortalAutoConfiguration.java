package com.mkt.tracking;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

/** Portal controllers only when portal-app is on the classpath (03 §6.1). */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.portal.PortalApplication")
@ComponentScan(basePackages = "com.mkt.tracking.controller.portal")
public class TrackingPortalAutoConfiguration {}
