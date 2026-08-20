package com.mkt.identity;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.identity.application.AdminUserStore;
import com.mkt.identity.support.AdminStpInterface;
import com.mkt.identity.support.CsrfFilter;
import com.mkt.identity.support.ForbiddenAuditSink;
import com.mkt.identity.support.SessionAuthFilter;
import com.mkt.identity.support.SessionSide;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpAdmin;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
@ComponentScan(basePackages = "com.mkt.identity.controller.admin")
@EnableAspectJAutoProxy
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
            KickReasonStore kickReasons, SessionAvailability availability, ForbiddenAuditSink forbiddenAudit) {
        FilterRegistrationBean<SessionAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new SessionAuthFilter(SessionSide.ADMIN, kickReasons, availability, forbiddenAudit));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    WebMvcConfigurer adminSaInterceptorConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new SaInterceptor()).addPathPatterns("/admin/**");
            }
        };
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
