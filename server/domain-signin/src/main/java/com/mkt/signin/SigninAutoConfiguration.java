package com.mkt.signin;

import com.mkt.signin.support.SigninSettings;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@AutoConfigureBefore(name = "com.mkt.identity.IdentityAutoConfiguration")
@ConditionalOnBean(DataSource.class)
@Import(SigninPersistenceScan.class)
public class SigninAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SigninSettings signinSettings() {
        return new SigninSettings();
    }
}
