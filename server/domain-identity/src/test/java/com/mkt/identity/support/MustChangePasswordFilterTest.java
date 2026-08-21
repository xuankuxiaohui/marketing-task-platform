package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mkt.identity.application.AdminUserStore;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MustChangePasswordFilterTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void allowsPasswordChangeAndBlocksOtherWrites() throws Exception {
        AdminUserStore users = Mockito.mock(AdminUserStore.class);
        AdminUserEntity user = new AdminUserEntity();
        user.setId(1L);
        user.setMustChangePassword(1);
        when(users.getById(1L)).thenReturn(user);
        MustChangePasswordFilter filter = new MustChangePasswordFilter(SessionSide.ADMIN, users, null);
        UserContext.set(new UserPrincipal(1L, "admin", "admin"));

        MockHttpServletResponse allowed = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        MockHttpServletRequest password = new MockHttpServletRequest("PUT", "/admin/auth/password");
        filter.doFilter(password, allowed, chain);
        assertThat(allowed.getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/admin/identity/users");
        filter.doFilter(other, blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(blocked.getContentAsString()).contains("auth.password.must-change");
    }
}
