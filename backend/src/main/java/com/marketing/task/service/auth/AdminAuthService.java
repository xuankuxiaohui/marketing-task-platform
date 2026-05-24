package com.marketing.task.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.domain.entity.AdminUser;
import com.marketing.task.mapper.AdminUserMapper;
import com.marketing.task.security.AdminJwtProvider;
import com.marketing.task.security.AuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private final AdminUserMapper adminUserMapper;
    private final AdminJwtProvider adminJwtProvider;
    private final PasswordEncoder passwordEncoder;

    public LoginResult login(String username, String password) {
        AdminUser user = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (user == null) {
            throw new AuthenticationException("用户名或密码错误");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new AuthenticationException("账号已停用");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("用户名或密码错误");
        }
        String token = adminJwtProvider.issue(String.valueOf(user.getId()));
        return new LoginResult(token, String.valueOf(user.getId()), user.getUsername(),
                user.getNickname() != null ? user.getNickname() : user.getUsername());
    }

    public record LoginResult(String token, String userId, String username, String nickname) {
    }
}
