package com.marketing.system.service.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.system.domain.entity.AdminUser;
import com.marketing.system.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserManagementService {
    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    public IPage<AdminUser> list(long page, long size, String keyword) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(AdminUser::getUsername, keyword)
                .or()
                .like(AdminUser::getNickname, keyword));
        }
        wrapper.orderByDesc(AdminUser::getId);
        return adminUserMapper.selectPage(Page.of(page, size), wrapper);
    }

    @Transactional
    public AdminUser create(String username, String password, String nickname) {
        Long existing = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        adminUserMapper.insert(user);
        log.info("创建管理员: id={}, username={}", user.getId(), username);
        return user;
    }

    @Transactional
    public String resetPassword(Long userId) {
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        String newPassword = generateRandomPassword(10);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserMapper.updateById(user);
        return newPassword;
    }

    @Transactional
    public boolean toggleEnabled(Long userId) {
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        boolean newState = !Boolean.TRUE.equals(user.getEnabled());
        user.setEnabled(newState);
        adminUserMapper.updateById(user);
        if (!newState) {
            try {
                StpUtil.kickout(String.valueOf(userId));
            } catch (Exception e) {
                log.debug("Kickout after disable skipped for admin user {}: {}", userId, e.getMessage());
            }
        }
        return newState;
    }

    public void kick(Long userId) {
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        StpUtil.kickout(String.valueOf(userId));
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
