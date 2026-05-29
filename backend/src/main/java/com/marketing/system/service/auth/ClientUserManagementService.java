package com.marketing.system.service.auth;

import cn.dev33.satoken.stp.StpLogic;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.system.domain.entity.ClientUser;
import com.marketing.system.mapper.ClientUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Slf4j
@Service
public class ClientUserManagementService {
    private final ClientUserMapper clientUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final StpLogic clientStpLogic;

    public ClientUserManagementService(ClientUserMapper clientUserMapper,
                                        PasswordEncoder passwordEncoder,
                                        @Qualifier("clientStpLogic") StpLogic clientStpLogic) {
        this.clientUserMapper = clientUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.clientStpLogic = clientStpLogic;
    }

    public IPage<ClientUser> list(long page, long size, String keyword) {
        LambdaQueryWrapper<ClientUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(ClientUser::getUsername, keyword)
                .or()
                .like(ClientUser::getNickname, keyword)
                .or()
                .like(ClientUser::getProvince, keyword)
                .or()
                .like(ClientUser::getRole, keyword));
        }
        wrapper.orderByDesc(ClientUser::getId);
        return clientUserMapper.selectPage(Page.of(page, size), wrapper);
    }

    @Transactional
    public String resetPassword(Long userId) {
        ClientUser user = clientUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        String newPassword = generateRandomPassword(10);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        clientUserMapper.updateById(user);
        return newPassword;
    }

    @Transactional
    public boolean toggleEnabled(Long userId) {
        ClientUser user = clientUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        boolean newState = !Boolean.TRUE.equals(user.getEnabled());
        user.setEnabled(newState);
        clientUserMapper.updateById(user);
        if (!newState) {
            try {
                clientStpLogic.kickout(String.valueOf(userId));
            } catch (Exception e) {
                log.debug("Kickout after disable skipped for client user {}: {}", userId, e.getMessage());
            }
        }
        return newState;
    }

    public void kick(Long userId) {
        ClientUser user = clientUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        clientStpLogic.kickout(String.valueOf(userId));
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
