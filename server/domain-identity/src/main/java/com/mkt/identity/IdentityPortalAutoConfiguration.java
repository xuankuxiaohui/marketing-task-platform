package com.mkt.identity;

import cn.dev33.satoken.stp.StpUtil;
import com.mkt.identity.support.SessionAuthFilter;
import com.mkt.identity.support.SessionSide;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.portal.PortalApplication")
@ComponentScan(basePackages = "com.mkt.identity.controller.portal")
public class IdentityPortalAutoConfiguration {

    @Bean
    FilterRegistrationBean<SessionAuthFilter> portalSessionAuthFilter(
            KickReasonStore kickReasons, SessionAvailability availability) {
        StpUtil.setStpLogic(StpClient.LOGIC);
        FilterRegistrationBean<SessionAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new SessionAuthFilter(SessionSide.PORTAL, kickReasons, availability));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
