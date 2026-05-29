package com.marketing.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenRouteConfig implements WebMvcConfigurer {

    private final StpLogic clientStpLogic;

    @Value("${app.auth.mock-enabled:false}")
    private boolean mockEnabled;

    public SaTokenRouteConfig(@Qualifier("clientStpLogic") StpLogic clientStpLogic) {
        this.clientStpLogic = clientStpLogic;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (mockEnabled) {
            return;
        }

        // Admin routes — use StpUtil (configured with admin StpLogic in SaTokenConfig)
        registry.addInterceptor(new SaInterceptor(handle -> {
                    SaRouter.match("/api/admin/**")
                            .notMatch("/api/admin/auth/login")
                            .check(r -> StpUtil.checkLogin());
                }))
                .addPathPatterns("/api/admin/**");

        // Client routes — use clientStpLogic directly
        registry.addInterceptor(new SaInterceptor(handle -> {
                    SaRouter.match("/api/client/**")
                            .notMatch("/api/client/auth/login", "/api/client/auth/register")
                            .check(r -> clientStpLogic.checkLogin());
                }))
                .addPathPatterns("/api/client/**");
    }
}
