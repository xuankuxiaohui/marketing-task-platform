package com.marketing.system.service;

import com.marketing.system.domain.entity.AdminUser;
import com.marketing.system.domain.entity.OperationLog;
import com.marketing.system.mapper.AdminUserMapper;
import com.marketing.system.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {
    private final OperationLogMapper operationLogMapper;
    private final AdminUserMapper adminUserMapper;

    @Async
    public void record(String operatorId, String operationType, String targetType,
                       Long targetId, String targetName, String detail) {
        try {
            OperationLog log = new OperationLog();
            log.setOperatorId(operatorId);
            log.setOperatorName(resolveOperatorName(operatorId));
            log.setOperationType(operationType);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setTargetName(targetName);
            log.setDetail(detail);
            operationLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("Failed to record operation log: operatorId={}, operationType={}, targetType={}, targetId={}",
                    operatorId, operationType, targetType, targetId, e);
        }
    }

    private String resolveOperatorName(String operatorId) {
        try {
            Long id = Long.parseLong(operatorId);
            AdminUser user = adminUserMapper.selectById(id);
            if (user != null) {
                return user.getNickname() != null ? user.getNickname() : user.getUsername();
            }
        } catch (NumberFormatException ignored) {
        }
        return operatorId;
    }
}
