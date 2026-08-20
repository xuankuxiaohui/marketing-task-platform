package com.mkt.identity.application;

import com.mkt.identity.command.SessionKickCommand;
import com.mkt.identity.domain.AccountTypes;
import com.mkt.identity.domain.Usernames;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.query.SessionQuery;
import com.mkt.identity.response.SessionView;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SessionAdminAppService {

    private final SessionService sessions;
    private final AdminUserStore adminUsers;
    private final PortalUserStore portalUsers;

    public SessionAdminAppService(
            SessionService sessions, AdminUserStore adminUsers, PortalUserStore portalUsers) {
        this.sessions = sessions;
        this.adminUsers = adminUsers;
        this.portalUsers = portalUsers;
    }

    public PageData<SessionView> page(SessionQuery query) {
        String accountType = blankToNull(query.accountType());
        if (accountType != null && !AccountTypes.valid(accountType)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "账号类型不合法");
        }
        String account = Usernames.normalize(query.account());
        List<SessionView> rows = new ArrayList<>();
        if (accountType == null || AccountTypes.ADMIN.equals(accountType)) {
            rows.addAll(sessions.listAdmin(resolveAdminId(account, accountType != null)));
        }
        if (accountType == null || AccountTypes.PORTAL.equals(accountType)) {
            rows.addAll(sessions.listClient(resolvePortalId(account, accountType != null)));
        }
        rows.sort(Comparator.comparing(SessionView::loginAt, Comparator.nullsLast(Comparator.reverseOrder())));
        long total = rows.size();
        int from = (int) Math.min(total, query.page().offset());
        int to = (int) Math.min(total, from + query.page().pageSize());
        return new PageData<>(total, rows.subList(from, to));
    }

    public void kick(SessionKickCommand command) {
        if (!AccountTypes.valid(command.accountType())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "账号类型不合法");
        }
        String account = Usernames.normalize(command.account());
        if (account == null || account.isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "账号不能为空");
        }
        if (AccountTypes.ADMIN.equals(command.accountType())) {
            sessions.kickAllAdmin(requireAdminId(account));
            return;
        }
        sessions.kickAllClient(requirePortalId(account));
    }

    private Long resolveAdminId(String account, boolean required) {
        if (account == null || account.isBlank()) {
            return null;
        }
        AdminUserEntity user = adminUsers.getByUsername(account);
        if (user == null) {
            if (required) {
                throw new BusinessException(CommonErrorCodes.NOT_FOUND);
            }
            return -1L;
        }
        return user.getId();
    }

    private Long resolvePortalId(String account, boolean required) {
        if (account == null || account.isBlank()) {
            return null;
        }
        PortalUserEntity user = portalUsers.getByUsername(account);
        if (user == null) {
            if (required) {
                throw new BusinessException(CommonErrorCodes.NOT_FOUND);
            }
            return -1L;
        }
        return user.getId();
    }

    private long requireAdminId(String account) {
        AdminUserEntity user = adminUsers.getByUsername(account);
        if (user == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return user.getId();
    }

    private long requirePortalId(String account) {
        PortalUserEntity user = portalUsers.getByUsername(account);
        if (user == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return user.getId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
