package com.mkt.admin.simulate;

import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.application.PrizeStore;
import com.mkt.reward.application.StockLogStore;
import com.mkt.task.application.TaskClaimAppService;
import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.application.TaskPortalAppService;
import com.mkt.task.application.TaskStepAppService;
import com.mkt.task.application.TaskVersionSnapshotStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SimulateConfiguration {

    @Bean
    SimulateGrantLookup simulateGrantLookup(JdbcTemplate jdbc) {
        return new JdbcSimulateGrantLookup(jdbc);
    }

    @Bean
    SimulateTaskService simulateTaskService(
            TaskPortalAppService portal,
            TaskClaimAppService claims,
            TaskStepAppService steps,
            TaskInstanceStore instances,
            TaskVersionSnapshotStore snapshots,
            SimulateGrantLookup grants,
            PrizeStore prizes,
            StockLogStore stockLogs,
            PointsAppService points,
            Clock clock) {
        return new SimulateTaskService(
                portal, claims, steps, instances, snapshots, grants, prizes, stockLogs, points, clock);
    }
}
