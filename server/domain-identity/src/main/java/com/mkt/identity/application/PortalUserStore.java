package com.mkt.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.LoginLock;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.mapper.PortalUserMapper;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public class PortalUserStore {

    private final PortalUserMapper mapper;

    public PortalUserStore(PortalUserMapper mapper) {
        this.mapper = mapper;
    }

    public PortalUserEntity getById(long id) {
        return mapper.selectById(id);
    }

    public PortalUserEntity getByUsername(String username) {
        return mapper.selectOne(
                new LambdaQueryWrapper<PortalUserEntity>()
                        .eq(PortalUserEntity::getUsername, username)
                        .eq(PortalUserEntity::getDeleted, 0)
                        .last("LIMIT 1"));
    }

    public boolean usernameTaken(String username) {
        Long count = mapper.selectCount(
                new LambdaQueryWrapper<PortalUserEntity>().eq(PortalUserEntity::getUsername, username));
        return count != null && count > 0;
    }

    public PortalUserEntity insert(PortalUserEntity entity) {
        mapper.insert(entity);
        return entity;
    }

    public void updateNickname(long id, String nickname) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<PortalUserEntity>()
                        .eq(PortalUserEntity::getId, id)
                        .set(PortalUserEntity::getNickname, nickname));
    }

    public void saveLock(long id, LoginLock.State state) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<PortalUserEntity>()
                        .eq(PortalUserEntity::getId, id)
                        .set(PortalUserEntity::getFailedAttempts, state.failedAttempts())
                        .set(PortalUserEntity::getLockedUntil, IdentityTime.toUtc(state.lockedUntil())));
    }

    public void markLoginSuccess(long id, Instant now) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<PortalUserEntity>()
                        .eq(PortalUserEntity::getId, id)
                        .set(PortalUserEntity::getFailedAttempts, 0)
                        .set(PortalUserEntity::getLockedUntil, null)
                        .set(PortalUserEntity::getLastLoginAt, IdentityTime.toUtc(now)));
    }

    public void updatePassword(long id, String passwordHash) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<PortalUserEntity>()
                        .eq(PortalUserEntity::getId, id)
                        .set(PortalUserEntity::getPasswordHash, passwordHash)
                        .set(PortalUserEntity::getMustChangePassword, 0));
    }
}
