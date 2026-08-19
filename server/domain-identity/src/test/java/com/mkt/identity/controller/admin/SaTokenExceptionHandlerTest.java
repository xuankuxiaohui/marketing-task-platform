package com.mkt.identity.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.exception.NotPermissionException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class SaTokenExceptionHandlerTest {

    @Test
    void notPermissionIs403CommonCode() {
        ResponseEntity<Result<Void>> response =
                new SaTokenExceptionHandler().handleNotPermission(new NotPermissionException("identity:role:query"));
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(CommonErrorCodes.PERMISSION_DENIED.code());
    }
}
