package com.mkt.task;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

/** Portal controllers only when portal-app is on the classpath (03 §6.1). */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.portal.PortalApplication")
@ComponentScan(basePackages = "com.mkt.task.controller.portal")
public class TaskPortalAutoConfiguration {}
