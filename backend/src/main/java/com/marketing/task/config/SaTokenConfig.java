package com.marketing.task.config;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SaTokenConfig {

    public static final String ADMIN_TYPE = "admin";
    public static final String CLIENT_TYPE = "client";

    @PostConstruct
    public void setAdminStpLogic() {
        StpUtil.setStpLogic(new StpLogicJwtForSimple(ADMIN_TYPE));
        log.info("Sa-Token Admin StpLogic configured on StpUtil");
    }

    @Bean
    public StpLogic clientStpLogic() {
        log.info("Sa-Token Client StpLogic bean created");
        return new StpLogicJwtForSimple(CLIENT_TYPE);
    }
}
