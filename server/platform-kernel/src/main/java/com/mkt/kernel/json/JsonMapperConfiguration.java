package com.mkt.kernel.json;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

/** Exposes {@link JsonUtil} as the Spring {@link JsonMapper} so HTTP and code share one instance. */
@Configuration(proxyBeanMethods = false)
public class JsonMapperConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(JsonMapper.class)
    JsonMapper jsonMapper() {
        return JsonUtil.mapper();
    }
}
