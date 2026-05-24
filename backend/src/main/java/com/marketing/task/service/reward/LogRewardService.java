package com.marketing.task.service.reward;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.domain.entity.RewardRecord;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.RewardStatus;
import com.marketing.task.domain.reward.RewardConfig;
import com.marketing.task.mapper.RewardRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class LogRewardService implements RewardService {
    private final List<RewardHandler> handlers;
    private final RewardConfigParser configParser;
    private final RewardRecordMapper rewardRecordMapper;

    public LogRewardService(List<RewardHandler> handlers, RewardConfigParser configParser,
                            RewardRecordMapper rewardRecordMapper) {
        this.handlers = handlers;
        this.configParser = configParser;
        this.rewardRecordMapper = rewardRecordMapper;
    }

    @Override
    public void reward(UserTaskInstance instance, TaskStep rewardStep) {
        String idempotentKey = instance.getId() + ":" + rewardStep.getId();

        RewardRecord existing = rewardRecordMapper.selectOne(
                new LambdaQueryWrapper<RewardRecord>()
                        .eq(RewardRecord::getInstanceId, instance.getId())
                        .eq(RewardRecord::getStepId, rewardStep.getId()));

        if (existing != null && RewardStatus.SUCCESS.name().equals(existing.getStatus())) {
            log.info("奖励已发放，跳过: instanceId={}, stepId={}", instance.getId(), rewardStep.getId());
            return;
        }

        RewardConfig config = configParser.parse(rewardStep.getRewardConfigJson());
        RewardHandler handler = findHandler(config);

        RewardRecord record = existing != null ? existing : new RewardRecord();
        record.setInstanceId(instance.getId());
        record.setStepId(rewardStep.getId());
        record.setRewardType(config.getType());
        record.setRewardConfigJson(rewardStep.getRewardConfigJson());
        record.setIdempotentKey(idempotentKey);
        record.setStatus(RewardStatus.PENDING.name());

        if (existing != null) {
            rewardRecordMapper.updateById(record);
        } else {
            record.setCreatedAt(LocalDateTime.now());
            rewardRecordMapper.insert(record);
        }

        try {
            handler.distribute(instance, rewardStep, config);
            record.setStatus(RewardStatus.SUCCESS.name());
            record.setErrorMessage(null);
            rewardRecordMapper.updateById(record);
            log.info("奖励发放成功: type={}, instanceId={}", config.getType(), instance.getId());
        } catch (Exception e) {
            record.setStatus(RewardStatus.FAILED.name());
            record.setErrorMessage(e.getMessage());
            rewardRecordMapper.updateById(record);
            log.error("奖励发放失败: type={}, instanceId={}", config.getType(), instance.getId(), e);
        }
    }

    private RewardHandler findHandler(RewardConfig config) {
        for (RewardHandler handler : handlers) {
            if (handler.supports(config)) {
                return handler;
            }
        }
        throw new BusinessException(ErrorCode.REWARD_HANDLER_NOT_FOUND, "未找到匹配的发奖处理器: " + config.getType());
    }
}
