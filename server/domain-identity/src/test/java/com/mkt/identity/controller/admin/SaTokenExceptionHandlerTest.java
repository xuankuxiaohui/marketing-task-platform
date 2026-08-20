package com.mkt.identity.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.mkt.identity.support.SessionAuthFilter;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.Result;
import com.mkt.kernel.SessionErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class SaTokenExceptionHandlerTest {

    @Test
    void notPermissionIs403CommonCodeAndMarksRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ResponseEntity<Result<Void>> response = new SaTokenExceptionHandler()
                .handleNotPermission(new NotPermissionException("identity:role:query"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(CommonErrorCodes.PERMISSION_DENIED.code());
        assertThat(request.getAttribute(SessionAuthFilter.RBAC_DENIED)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void notLoginIs401Invalid() {
        ResponseEntity<Result<Void>> response =
                new SaTokenExceptionHandler().handleNotLogin(new NotLoginException("unlogin", "admin", "-1"));
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(SessionErrorCodes.INVALID.code());
    }
}
