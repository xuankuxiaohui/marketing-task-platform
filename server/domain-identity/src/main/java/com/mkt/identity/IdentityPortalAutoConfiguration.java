package com.mkt.identity;

import cn.dev33.satoken.stp.StpUtil;
import com.mkt.identity.application.InternalAppSecretCache;
import com.mkt.identity.application.InternalAppSecretCipher;
import com.mkt.identity.application.InternalHmacVerifier;
import com.mkt.identity.application.PortalUserStore;
import com.mkt.identity.config.ConfigService;
import com.mkt.identity.mapper.InternalAppMapper;
import com.mkt.identity.support.InternalAuthFilter;
import com.mkt.identity.support.MustChangePasswordFilter;
import com.mkt.identity.support.SessionAuthFilter;
import com.mkt.identity.support.SessionSide;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.nonce.NonceStore;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpClient;
import java.time.Clock;
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

    @Bean
    FilterRegistrationBean<MustChangePasswordFilter> portalMustChangePasswordFilter(PortalUserStore users) {
        FilterRegistrationBean<MustChangePasswordFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new MustChangePasswordFilter(SessionSide.PORTAL, null, users));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    FilterRegistrationBean<InternalAuthFilter> portalInternalAuthFilter(
            InternalAppMapper apps,
            InternalAppSecretCipher cipher,
            NonceStore nonceStore,
            SlidingWindowRateLimiter limiter,
            ConfigService configs,
            Clock clock) {
        InternalHmacVerifier verifier =
                new InternalHmacVerifier(apps, cipher, new InternalAppSecretCache(), nonceStore, configs, clock);
        FilterRegistrationBean<InternalAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new InternalAuthFilter(verifier, limiter, configs));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
