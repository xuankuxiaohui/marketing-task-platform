package com.marketing.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TaskPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskPlatformApplication.class, args);
    }
}
