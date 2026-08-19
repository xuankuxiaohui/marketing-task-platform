package com.mkt.identity;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AdminUserStore;
import com.mkt.identity.support.AdminStpInterface;
import com.mkt.identity.support.CsrfFilter;
import com.mkt.identity.support.SessionAuthFilter;
import com.mkt.identity.support.SessionSide;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpAdmin;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
@ConditionalOnBean(AdminAuthService.class)
@ComponentScan(basePackages = "com.mkt.identity.controller.admin")
public class IdentityAdminAutoConfiguration {

    @Bean
    AdminStpInterface adminStpInterface(AdminUserStore users) {
        AdminStpInterface stp = new AdminStpInterface(users);
        StpUtil.setStpLogic(StpAdmin.LOGIC);
        SaManager.setStpInterface(stp);
        return stp;
    }

    @Bean
    FilterRegistrationBean<SessionAuthFilter> adminSessionAuthFilter(
            KickReasonStore kickReasons, SessionAvailability availability) {
        FilterRegistrationBean<SessionAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new SessionAuthFilter(SessionSide.ADMIN, kickReasons, availability));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    FilterRegistrationBean<CsrfFilter> adminCsrfFilter() {
        FilterRegistrationBean<CsrfFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new CsrfFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
