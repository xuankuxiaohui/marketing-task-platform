package com.mkt.activity;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.portal.PortalApplication")
@ComponentScan(basePackages = "com.mkt.activity.controller.portal")
public class ActivityPortalAutoConfiguration {}
