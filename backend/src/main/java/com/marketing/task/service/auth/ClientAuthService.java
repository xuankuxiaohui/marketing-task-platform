package com.marketing.task.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.ClientUser;
import com.marketing.task.domain.enums.Platform;
import com.marketing.task.mapper.ClientUserMapper;
import com.marketing.task.security.AuthenticationException;
import com.marketing.task.security.ClientJwtProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientAuthService {
    private final ClientUserMapper clientUserMapper;
    private final ClientJwtProvider clientJwtProvider;
    private final PasswordEncoder passwordEncoder;

    public LoginResult register(RegisterRequest req) {
        if (clientUserMapper.exists(new LambdaQueryWrapper<ClientUser>().eq(ClientUser::getUsername, req.username()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        ClientUser user = new ClientUser();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname() != null ? req.nickname() : req.username());
        user.setProvince(req.province());
        user.setRole(req.role());
        user.setTags(req.tags());
        user.setOrgId(req.orgId());
        user.setLevel(req.level());
        user.setEnabled(true);
        clientUserMapper.insert(user);
        return login(req.username(), req.password());
    }

    public LoginResult login(String username, String password) {
        ClientUser user = clientUserMapper.selectOne(
                new LambdaQueryWrapper<ClientUser>().eq(ClientUser::getUsername, username));
        if (user == null) {
            throw new AuthenticationException("用户名或密码错误");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new AuthenticationException("账号已停用");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("用户名或密码错误");
        }
        UserContext context = buildUserContext(user);
        String token = clientJwtProvider.issue(context);
        return new LoginResult(token, String.valueOf(user.getId()), user.getUsername(),
                user.getNickname() != null ? user.getNickname() : user.getUsername());
    }

    private UserContext buildUserContext(ClientUser user) {
        Set<String> tags = user.getTags() == null || user.getTags().isBlank()
                ? Set.of()
                : Arrays.stream(user.getTags().split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        return UserContext.builder()
                .userId(String.valueOf(user.getId()))
                .province(user.getProvince())
                .role(user.getRole())
                .tags(tags)
                .orgId(user.getOrgId())
                .level(user.getLevel())
                .platform(Platform.WEB)
                .build();
    }

    public ClientUser getById(Long id) {
        return clientUserMapper.selectById(id);
    }

    public record LoginResult(String token, String userId, String username, String nickname) {
    }

    public record RegisterRequest(String username, String password, String nickname,
                                  String province, String role, String tags, String orgId, Integer level) {
    }
}
