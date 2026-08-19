package com.mkt.task;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

/**
 * Admin controllers only when admin-app is on the classpath (03 §6.1).
 * Portal does not assemble domain-task in this task (C 端接线见任务 28).
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
@ComponentScan(basePackages = "com.mkt.task.controller.admin")
public class TaskAdminAutoConfiguration {}
