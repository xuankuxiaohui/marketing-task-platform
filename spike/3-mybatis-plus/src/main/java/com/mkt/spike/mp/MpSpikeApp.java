package com.mkt.spike.mp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.mkt.spike.mp.mapper")
public class MpSpikeApp {

    public static void main(String[] args) {
        SpringApplication.run(MpSpikeApp.class, args);
    }
}
