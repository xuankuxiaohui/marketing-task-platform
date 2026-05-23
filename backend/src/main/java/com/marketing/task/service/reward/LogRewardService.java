package com.marketing.task.service.reward;

import com.marketing.task.common.BusinessException;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.reward.RewardConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class LogRewardService implements RewardService {
    private final List<RewardHandler> handlers;
    private final RewardConfigParser configParser;

    public LogRewardService(List<RewardHandler> handlers, RewardConfigParser configParser) {
        this.handlers = handlers;
        this.configParser = configParser;
    }

    @Override
    public void reward(UserTaskInstance instance, TaskStep rewardStep) {
        if (instance.getRewardTime() != null) {
            log.info("奖励已发放，跳过: instanceId={}", instance.getId());
            return;
        }
        RewardConfig config = configParser.parse(rewardStep.getRewardConfigJson());
        for (RewardHandler handler : handlers) {
            if (handler.supports(config)) {
                handler.distribute(instance, rewardStep, config);
                return;
            }
        }
        log.warn("未找到匹配的发奖处理器 type={}, instanceId={}",
                config.getType(), instance.getId());
        throw new BusinessException("未找到匹配的发奖处理器");
    }
}
