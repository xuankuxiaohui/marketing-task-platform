package com.mkt.reward;

import com.mkt.contract.RewardPort;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.application.PrizeStore;
import com.mkt.reward.points.PointsPort;
import com.mkt.reward.points.PointsPortStub;
import com.mkt.reward.port.RewardPortImpl;
import com.mkt.reward.support.RewardGrantSettings;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Reward domain beans. Persistence scan is imported so this class can keep
 * {@code @ConditionalOnBean(DataSource)} without pairing {@code @ComponentScan}.
 */
@AutoConfiguration
@AutoConfigureBefore(name = "com.mkt.identity.IdentityAutoConfiguration")
@ConditionalOnBean(DataSource.class)
@Import(RewardPersistenceScan.class)
public class RewardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RewardGrantSettings rewardGrantSettings() {
        return new RewardGrantSettings();
    }

    @Bean
    @ConditionalOnMissingBean(PointsPort.class)
    PointsPort pointsPortStub() {
        return new PointsPortStub();
    }

    @Bean
    @ConditionalOnMissingBean(RewardPort.class)
    RewardPort rewardPort(GrantAppService grants, PrizeStore prizes) {
        return new RewardPortImpl(grants, prizes);
    }
}
