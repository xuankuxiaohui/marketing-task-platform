package com.marketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.marketing")
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class TaskPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskPlatformApplication.class, args);
    }
}
