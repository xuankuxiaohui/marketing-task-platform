package com.marketing.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.activity.domain.dto.*;
import com.marketing.activity.domain.entity.Activity;
import com.marketing.activity.domain.entity.ActivityDisplayRule;
import com.marketing.activity.domain.enums.ActivityStatus;
import com.marketing.activity.domain.enums.GrayType;
import com.marketing.activity.mapper.ActivityDisplayRuleMapper;
import com.marketing.activity.mapper.ActivityMapper;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.prize.domain.entity.Prize;
import com.marketing.task.domain.entity.Task;
import com.marketing.prize.mapper.PrizeMapper;
import com.marketing.signin.mapper.SignInConfigMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.signin.domain.entity.SignInConfig;
import com.marketing.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivityDisplayRuleMapper displayRuleMapper;
    private final ActivityCacheService cacheService;
    private final TaskMapper taskMapper;
    private final SignInConfigMapper signInConfigMapper;
    private final PrizeMapper prizeMapper;

    // --- CRUD ---

    public Page<Activity> list(int page, int size, String status) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<Activity>()
                .eq(Activity::getDeleted, 0)
                .orderByDesc(Activity::getCreatedAt);
        if (status != null) {
            wrapper.eq(Activity::getStatus, status);
        }
        return activityMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public ActivityDetailVO getDetail(Long id) {
        Activity activity = requireActivity(id);
        return toDetailVO(activity);
    }

    @Transactional
    public ActivityDetailVO create(ActivityCreateRequest request, String operatorId) {
        Long existing = activityMapper.selectCount(
                new LambdaQueryWrapper<Activity>().eq(Activity::getCode, request.getCode()));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.ACTIVITY_CODE_EXISTS, "活动编码已存在: " + request.getCode());
        }
        Activity activity = new Activity();
        activity.setCode(request.getCode());
        activity.setName(request.getName());
        activity.setDescription(request.getDescription());
        activity.setGrayType(request.getGrayType());
        activity.setGrayConfig(request.getGrayConfig());
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束时间不能早于开始时间");
        }
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setParticipationRules(request.getParticipationRules());
        activity.setStatus(ActivityStatus.DRAFT.name());
        activity.setCacheVersion(1);
        activity.setCreatedAt(LocalDateTime.now());
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setCreatedBy(operatorId);
        activity.setUpdatedBy(operatorId);
        activity.setDeleted(0);
        activityMapper.insert(activity);
        log.info("创建活动: id={}, code={}, name={}, status={}", activity.getId(), activity.getCode(), activity.getName(), activity.getStatus());
        return getDetail(activity.getId());
    }

    @Transactional
    public ActivityDetailVO update(Long id, ActivityUpdateRequest request, String operatorId) {
        Activity activity = requireActivity(id);
        if (!isEditable(activity)) {
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS, "仅DRAFT和OFFLINE状态可编辑");
        }
        if (request.getName() != null) activity.setName(request.getName());
        if (request.getDescription() != null) activity.setDescription(request.getDescription());
        if (request.getGrayType() != null) activity.setGrayType(request.getGrayType());
        if (request.getGrayConfig() != null) activity.setGrayConfig(request.getGrayConfig());
        if (request.getStartTime() != null) activity.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) activity.setEndTime(request.getEndTime());
        if (request.getParticipationRules() != null) activity.setParticipationRules(request.getParticipationRules());
        LocalDateTime effectiveStart = request.getStartTime() != null ? request.getStartTime() : activity.getStartTime();
        LocalDateTime effectiveEnd = request.getEndTime() != null ? request.getEndTime() : activity.getEndTime();
        if (effectiveEnd.isBefore(effectiveStart)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束时间不能早于开始时间");
        }
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setUpdatedBy(operatorId);
        activityMapper.updateById(activity);
        cacheService.evictActivity(id);
        log.info("更新活动: id={}, operator={}", id, operatorId);
        return getDetail(id);
    }

    @Transactional
    public void delete(Long id, String operatorId) {
        Activity activity = requireActivity(id);
        if (!ActivityStatus.DRAFT.name().equals(activity.getStatus())) {
            log.warn("删除失败: id={}, 当前状态={}", id, activity.getStatus());
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS,
                    "仅DRAFT状态可删除，当前状态: " + activity.getStatus());
        }
        activity.setDeleted(1);
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setUpdatedBy(operatorId);
        activityMapper.updateById(activity);
        cacheService.evictActivity(id);
        log.info("删除活动: id={}", id);
    }

    // --- State Machine ---

    @Transactional
    public void publish(Long id, String operatorId) {
        Activity activity = requireActivity(id);
        String status = activity.getStatus();
        if (!ActivityStatus.DRAFT.name().equals(status) && !ActivityStatus.OFFLINE.name().equals(status)) {
            log.warn("发布失败: id={}, 当前状态={}", id, status);
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS,
                    "仅DRAFT或OFFLINE状态可发布，当前状态: " + status);
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "活动已过结束时间，无法发布");
        }
        activity.setStatus(ActivityStatus.PUBLISHED.name());
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setUpdatedBy(operatorId);
        activityMapper.updateById(activity);
        cacheService.evictActivity(id);
        log.info("发布活动: id={}, code={}", id, activity.getCode());
    }

    @Transactional
    public void offline(Long id, String operatorId) {
        Activity activity = requireActivity(id);
        String status = activity.getStatus();
        if (!ActivityStatus.PUBLISHED.name().equals(status) && !ActivityStatus.ONLINE.name().equals(status)) {
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS, "仅PUBLISHED和ONLINE状态可下线");
        }
        activity.setStatus(ActivityStatus.OFFLINE.name());
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setUpdatedBy(operatorId);
        activityMapper.updateById(activity);
        cacheService.evictActivity(id);
        log.info("下线活动: id={}, code={}", id, activity.getCode());
    }

    @Transactional
    public void backToDraft(Long id, String operatorId) {
        Activity activity = requireActivity(id);
        if (!ActivityStatus.OFFLINE.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS, "仅OFFLINE状态可退回草稿");
        }
        activity.setStatus(ActivityStatus.DRAFT.name());
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setUpdatedBy(operatorId);
        activityMapper.updateById(activity);
        cacheService.evictActivity(id);
        log.info("退回草稿: id={}, code={}", id, activity.getCode());
    }

    // --- Display Rule ---

    public ActivityDisplayRule getDisplayRule(Long activityId) {
        Activity activity = requireActivity(activityId);
        return cacheService.getDisplayRule(activity.getCode());
    }

    @Transactional
    public void updateDisplayRule(Long activityId, String content, String operatorId) {
        Activity activity = requireActivity(activityId);
        String activityCode = activity.getCode();
        String contentHash = sha256(content);
        ActivityDisplayRule rule = displayRuleMapper.selectOne(
                new LambdaQueryWrapper<ActivityDisplayRule>()
                        .eq(ActivityDisplayRule::getActivityCode, activityCode));
        if (rule == null) {
            rule = new ActivityDisplayRule();
            rule.setActivityCode(activityCode);
            rule.setContent(content);
            rule.setContentHash(contentHash);
            rule.setUpdatedAt(LocalDateTime.now());
            rule.setUpdatedBy(operatorId);
            try {
                displayRuleMapper.insert(rule);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                rule = displayRuleMapper.selectOne(
                        new LambdaQueryWrapper<ActivityDisplayRule>()
                                .eq(ActivityDisplayRule::getActivityCode, activityCode));
                rule.setContent(content);
                rule.setContentHash(contentHash);
                rule.setUpdatedAt(LocalDateTime.now());
                rule.setUpdatedBy(operatorId);
                displayRuleMapper.updateById(rule);
            }
        } else {
            rule.setContent(content);
            rule.setContentHash(contentHash);
            rule.setUpdatedAt(LocalDateTime.now());
            rule.setUpdatedBy(operatorId);
            displayRuleMapper.updateById(rule);
        }
        cacheService.evictDisplayRule(activityCode);
        log.info("更新展示规则: activityCode={}", activityCode);
    }

    // --- Sub-modules ---

    public ActivitySubModulesVO getSubModules(Long activityId) {
        Activity activity = requireActivity(activityId);
        String code = activity.getCode();

        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().eq(Task::getActivityCode, code).eq(Task::getDeleted, 0));
        List<SignInConfig> configs = signInConfigMapper.selectList(
                new LambdaQueryWrapper<SignInConfig>().eq(SignInConfig::getActivityCode, code));
        List<Prize> prizes = prizeMapper.selectList(
                new LambdaQueryWrapper<Prize>().eq(Prize::getActivityCode, code));

        List<ActivitySubModulesVO.TaskSummary> taskSummaries = tasks.stream().map(t -> {
            ActivitySubModulesVO.TaskSummary s = new ActivitySubModulesVO.TaskSummary();
            s.setId(t.getId());
            s.setName(t.getName());
            s.setStatus(t.getStatus());
            return s;
        }).toList();

        List<ActivitySubModulesVO.SignInConfigSummary> signSummaries = configs.stream().map(c -> {
            ActivitySubModulesVO.SignInConfigSummary s = new ActivitySubModulesVO.SignInConfigSummary();
            s.setId(c.getId());
            s.setName(c.getName());
            s.setStatus(c.getStatus());
            return s;
        }).toList();

        List<ActivitySubModulesVO.PrizeSummary> prizeSummaries = prizes.stream().map(p -> {
            ActivitySubModulesVO.PrizeSummary s = new ActivitySubModulesVO.PrizeSummary();
            s.setId(p.getId());
            s.setName(p.getName());
            s.setType(p.getType());
            return s;
        }).toList();

        return new ActivitySubModulesVO(taskSummaries, signSummaries, prizeSummaries);
    }

    // --- Gray ---

    public boolean isUserInGray(Activity activity, Long userId) {
        if (GrayType.NONE.name().equals(activity.getGrayType())) {
            return true;
        }
        if (GrayType.RATIO.name().equals(activity.getGrayType())) {
            Map<String, Object> config = JsonUtil.jsonToObj(activity.getGrayConfig(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            if (config == null) {
                return true;
            }
            int ratio = ((Number) config.get("ratio")).intValue();
            return (userId.hashCode() & 0x7FFFFFFF) % 100 < ratio;
        }
        return true;
    }

    // --- C-end list ---

    public List<Activity> listOnlineActivities() {
        return activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.ONLINE.name())
                        .eq(Activity::getDeleted, 0)
                        .orderByAsc(Activity::getStartTime));
    }

    // --- Helpers ---

    private Activity requireActivity(Long id) {
        Activity activity = cacheService.getActivity(id);
        if (activity == null || activity.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND, "活动不存在: " + id);
        }
        return activity;
    }

    private boolean isEditable(Activity activity) {
        String status = activity.getStatus();
        return ActivityStatus.DRAFT.name().equals(status) || ActivityStatus.OFFLINE.name().equals(status);
    }

    private ActivityDetailVO toDetailVO(Activity activity) {
        ActivityDetailVO vo = new ActivityDetailVO();
        vo.setId(activity.getId());
        vo.setCode(activity.getCode());
        vo.setName(activity.getName());
        vo.setDescription(activity.getDescription());
        vo.setStatus(activity.getStatus());
        vo.setGrayType(activity.getGrayType());
        vo.setGrayConfig(activity.getGrayConfig());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setParticipationRules(activity.getParticipationRules());
        vo.setHasDisplayRule(displayRuleMapper.selectOne(
                new LambdaQueryWrapper<ActivityDisplayRule>()
                        .eq(ActivityDisplayRule::getActivityCode, activity.getCode())) != null);
        vo.setCreatedAt(activity.getCreatedAt());
        vo.setUpdatedAt(activity.getUpdatedAt());
        return vo;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
