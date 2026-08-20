package com.mkt.identity.application;

import com.mkt.contract.InstanceCounts;
import com.mkt.contract.PrizeSummary;
import com.mkt.contract.RewardPort;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.TaskReadPort;
import com.mkt.contract.UserRewardSummary;
import com.mkt.contract.UserRiskSummary;
import com.mkt.identity.command.PortalUserProfileCommand;
import com.mkt.identity.command.PortalUserResetPasswordCommand;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.convert.PortalUserConvert;
import com.mkt.identity.domain.OrgIds;
import com.mkt.identity.domain.PasswordPolicies;
import com.mkt.identity.domain.ProfileTags;
import com.mkt.identity.domain.SqlLikes;
import com.mkt.identity.domain.UserStatuses;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.mapper.PortalUserMapper;
import com.mkt.identity.query.PortalUserQuery;
import com.mkt.identity.response.PortalUserDetailResponse;
import com.mkt.identity.response.PortalUserView;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.json.JsonUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalUserAppService {

    private final PortalUserMapper users;
    private final PasswordHasher hasher;
    private final SessionService sessions;
    private final UserAttributePortImpl attributes;
    private final RewardPort rewards;
    private final RiskCheckPort risk;
    private final TaskReadPort tasks;
    private final IdentityAuditAppender audits;
    private final Clock clock;

    public PortalUserAppService(
            PortalUserMapper users,
            PasswordHasher hasher,
            SessionService sessions,
            UserAttributePortImpl attributes,
            RewardPort rewards,
            RiskCheckPort risk,
            TaskReadPort tasks,
            IdentityAuditAppender audits,
            Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.attributes = attributes;
        this.rewards = rewards;
        this.risk = risk;
        this.tasks = tasks;
        this.audits = audits;
        this.clock = clock;
    }

    public PageData<PortalUserView> page(PortalUserQuery query) {
        String username = SqlLikes.containsOrNull(query.username());
        String nickname = SqlLikes.containsOrNull(query.nickname());
        String province = blankToNull(query.province());
        String level = blankToNull(query.level());
        String tag = blankToNull(query.tag());
        String status = blankToNull(query.status());
        if (status != null && !UserStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "用户状态不合法");
        }
        LocalDateTime from = IdentityTime.toUtc(query.registeredFrom());
        LocalDateTime to = IdentityTime.toUtc(query.registeredTo());
        long total = users.countPage(username, nickname, province, level, tag, status, from, to);
        List<PortalUserEntity> rows = users.listPage(
                username,
                nickname,
                province,
                level,
                tag,
                status,
                from,
                to,
                query.page().offset(),
                query.page().pageSize());
        List<PortalUserView> views = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (PortalUserEntity row : rows) {
                views.add(PortalUserConvert.toView(row));
            }
        }
        return new PageData<>(total, views);
    }

    public PortalUserDetailResponse detail(long id) {
        PortalUserEntity existing = requireActive(id);
        UserRewardSummary reward = rewards.userSummary(id);
        if (reward == null) {
            reward = new UserRewardSummary(0L, new PrizeSummary(0L, 0L));
        }
        UserRiskSummary riskSummary = risk.userSummary(id);
        InstanceCounts counts = tasks.instanceCounts(id);
        return PortalUserConvert.toDetail(
                existing, counts, reward.pointsBalance(), reward.prizeSummary(), riskSummary);
    }

    @Transactional
    public void updateProfile(long id, PortalUserProfileCommand command) {
        PortalUserEntity existing = requireActive(id);
        String orgId = command.orgId();
        if (orgId != null && !orgId.isBlank() && OrgIds.normalizeOrNull(orgId) == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "组织 ID 格式不正确");
        }
        List<String> tags = ProfileTags.normalize(command.tags());
        if (!ProfileTags.withinLimit(tags)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "标签最多 20 个");
        }
        existing.setProvince(blankToNull(command.province()));
        existing.setUserLevel(blankToNull(command.userLevel()));
        existing.setUserRole(blankToNull(command.userRole()));
        existing.setTags(JsonUtil.toJson(tags));
        existing.setOrgId(OrgIds.normalizeOrNull(orgId));
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        attributes.evictAfterCommit(id);
        audits.append(
                "portal-user-update-profile",
                "sys_portal_user",
                String.valueOf(id),
                "SUCCESS",
                summary("update-profile", existing.getUsername()));
    }

    @Transactional
    public void disable(long id) {
        PortalUserEntity existing = requireActive(id);
        existing.setStatus(UserStatuses.DISABLED);
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        sessions.logoutAllClient(id);
        attributes.evictAfterCommit(id);
        audits.append(
                "portal-user-disable",
                "sys_portal_user",
                String.valueOf(id),
                "SUCCESS",
                summary("disable", existing.getUsername()));
    }

    @Transactional
    public void enable(long id) {
        PortalUserEntity existing = requireActive(id);
        existing.setStatus(UserStatuses.ENABLED);
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        attributes.evictAfterCommit(id);
        audits.append(
                "portal-user-enable",
                "sys_portal_user",
                String.valueOf(id),
                "SUCCESS",
                summary("enable", existing.getUsername()));
    }

    @Transactional
    public void resetPassword(long id, PortalUserResetPasswordCommand command) {
        PortalUserEntity existing = requireActive(id);
        if (!PasswordPolicies.portalSatisfied(command.newPassword())) {
            throw new BusinessException(AuthErrorCodes.PASSWORD_POLICY_VIOLATED);
        }
        existing.setPasswordHash(hasher.hash(command.newPassword()));
        existing.setMustChangePassword(1);
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        sessions.logoutAllClient(id);
        audits.append(
                "portal-user-reset-password",
                "sys_portal_user",
                String.valueOf(id),
                "SUCCESS",
                summary("reset-password", existing.getUsername()));
    }

    @Transactional
    public void delete(long id) {
        PortalUserEntity existing = requireActive(id);
        existing.setDeleted(1);
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        sessions.logoutAllClient(id);
        attributes.evictAfterCommit(id);
        audits.append(
                "portal-user-delete",
                "sys_portal_user",
                String.valueOf(id),
                "SUCCESS",
                summary("delete", existing.getUsername()));
    }

    private PortalUserEntity requireActive(long id) {
        PortalUserEntity existing = users.selectById(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String summary(String action, String username) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("username", username);
        return JsonUtil.toJson(body);
    }
}
