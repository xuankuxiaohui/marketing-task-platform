package com.marketing.task.service.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.domain.entity.AdminUser;
import com.marketing.task.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

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
