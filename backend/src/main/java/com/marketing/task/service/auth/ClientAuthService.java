package com.marketing.task.service.auth;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpLogic;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.domain.entity.ClientUser;
import com.marketing.task.mapper.ClientUserMapper;
import com.marketing.task.security.AuthenticationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ClientAuthService {
    private final ClientUserMapper clientUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final StpLogic clientStpLogic;

    public ClientAuthService(ClientUserMapper clientUserMapper,
                             PasswordEncoder passwordEncoder,
                             @Qualifier("clientStpLogic") StpLogic clientStpLogic) {
        this.clientUserMapper = clientUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.clientStpLogic = clientStpLogic;
    }

    public LoginResult register(RegisterRequest req) {
        if (clientUserMapper.exists(new LambdaQueryWrapper<ClientUser>().eq(ClientUser::getUsername, req.username()))) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
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

        SaLoginModel loginModel = new SaLoginModel();
        loginModel.setExtra("province", user.getProvince());
        loginModel.setExtra("role", user.getRole());
        loginModel.setExtra("tags", user.getTags());
        loginModel.setExtra("orgId", user.getOrgId());
        loginModel.setExtra("level", user.getLevel());
        clientStpLogic.login(String.valueOf(user.getId()), loginModel);
        String token = clientStpLogic.getTokenValue();

        return new LoginResult(token, String.valueOf(user.getId()), user.getUsername(),
                user.getNickname() != null ? user.getNickname() : user.getUsername(),
                user.getProvince(), user.getRole(), user.getTags(), user.getOrgId(), user.getLevel());
    }

    public ClientUser getById(Long id) {
        return clientUserMapper.selectById(id);
    }

    public record LoginResult(String token, String userId, String username, String nickname,
                              String province, String role, String tags, String orgId, Integer level) {
    }

    public record RegisterRequest(String username, String password, String nickname,
                                  String province, String role, String tags, String orgId, Integer level) {
    }
}
