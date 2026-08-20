package com.mkt.identity;

import com.mkt.contract.RewardPort;
import com.mkt.contract.TaskReadPort;
import com.mkt.identity.application.RewardPortStub;
import com.mkt.identity.application.TaskReadPortStub;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@Import(IdentityPersistenceScan.class)
public class IdentityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RewardPort.class)
    RewardPort rewardPortStub() {
        return new RewardPortStub();
    }

    @Bean
    @ConditionalOnMissingBean(TaskReadPort.class)
    TaskReadPort taskReadPortStub() {
        return new TaskReadPortStub();
    }
}
