package com.marketing.task.service.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.task.domain.entity.ListData;
import com.marketing.task.mapper.ListDataMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrayService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ListDataMapper listDataMapper;

    public boolean isInGray(String userId, Long taskId, String grayType, String grayConfig) {
        if (grayType == null || "NONE".equals(grayType)) {
            return true;
        }
        if (userId == null || taskId == null) {
            return false;
        }
        try {
            return switch (grayType) {
                case "PERCENTAGE" -> isInPercentage(userId, taskId, grayConfig);
                case "AB" -> isInAB(userId, taskId, grayConfig);
                case "CROWD" -> isInCrowd(userId, grayConfig);
                default -> true;
            };
        } catch (Exception e) {
            log.warn("Gray evaluation error: userId={}, taskId={}, grayType={}", userId, taskId, grayType, e);
            return true;
        }
    }

    public String getABGroup(String userId, Long taskId, String grayConfig) {
        if (userId == null || taskId == null || grayConfig == null) return null;
        try {
            Map<String, Object> config = MAPPER.readValue(grayConfig, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
            if (groups == null || groups.isEmpty()) return null;
            int bucket = Math.abs((userId + taskId).hashCode() % 100);
            int cumulative = 0;
            for (Map<String, Object> group : groups) {
                Object pctObj = group.get("percent");
                int pct = pctObj instanceof Number n ? n.intValue() : 0;
                cumulative += pct;
                if (bucket < cumulative) {
                    return (String) group.get("name");
                }
            }
            return (String) groups.get(groups.size() - 1).get("name");
        } catch (Exception e) {
            log.warn("AB group assignment error: userId={}, taskId={}", userId, taskId, e);
            return null;
        }
    }

    private boolean isInPercentage(String userId, Long taskId, String grayConfig) throws Exception {
        int percent = 0;
        if (grayConfig != null) {
            Map<String, Object> config = MAPPER.readValue(grayConfig, new TypeReference<>() {});
            Object pct = config.get("percent");
            percent = pct instanceof Number n ? n.intValue() : 0;
        }
        if (percent <= 0) return false;
        if (percent >= 100) return true;
        int bucket = Math.abs((userId + taskId).hashCode() % 100);
        return bucket < percent;
    }

    private boolean isInAB(String userId, Long taskId, String grayConfig) {
        return getABGroup(userId, taskId, grayConfig) != null;
    }

    private boolean isInCrowd(String userId, String grayConfig) throws Exception {
        if (grayConfig == null) return false;
        Map<String, Object> config = MAPPER.readValue(grayConfig, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Object> crowdIds = (List<Object>) config.get("crowdIds");
        if (crowdIds == null || crowdIds.isEmpty()) return false;
        List<String> crowdIdStrs = crowdIds.stream().map(String::valueOf).toList();
        return listDataMapper.exists(new LambdaQueryWrapper<ListData>()
                .eq(ListData::getListType, "CROWD")
                .in(ListData::getListKey, crowdIdStrs)
                .eq(ListData::getUserId, userId));
    }
}
