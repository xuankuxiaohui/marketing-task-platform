package com.mkt.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.LoginLock;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.mapper.AdminUserMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserStore {

    private final AdminUserMapper mapper;

    public AdminUserStore(AdminUserMapper mapper) {
        this.mapper = mapper;
    }

    public AdminUserEntity getByUsername(String username) {
        return mapper.selectOne(
                new LambdaQueryWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getUsername, username)
                        .eq(AdminUserEntity::getDeleted, 0)
                        .last("LIMIT 1"));
    }

    public AdminUserEntity getById(long id) {
        return mapper.selectById(id);
    }

    public void saveLock(long id, LoginLock.State state) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getId, id)
                        .set(AdminUserEntity::getFailedAttempts, state.failedAttempts())
                        .set(AdminUserEntity::getLockedUntil, IdentityTime.toUtc(state.lockedUntil())));
    }

    public void markLoginSuccess(long id, Instant now) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getId, id)
                        .set(AdminUserEntity::getFailedAttempts, 0)
                        .set(AdminUserEntity::getLockedUntil, null)
                        .set(AdminUserEntity::getLastLoginAt, IdentityTime.toUtc(now)));
    }

    public void updatePassword(long id, String passwordHash) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getId, id)
                        .set(AdminUserEntity::getPasswordHash, passwordHash)
                        .set(AdminUserEntity::getMustChangePassword, 0));
    }

    public List<String> listRoleCodes(long userId) {
        List<String> codes = mapper.listRoleCodes(userId);
        return codes == null ? List.of() : List.copyOf(codes);
    }

    public List<String> listPermissionCodes(long userId) {
        List<String> codes = mapper.listPermissionCodes(userId);
        return codes == null ? List.of() : List.copyOf(codes);
    }
}
