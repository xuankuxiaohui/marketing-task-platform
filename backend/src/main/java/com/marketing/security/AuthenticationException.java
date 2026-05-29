package com.marketing.security;

import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;

public class AuthenticationException extends BusinessException {
    public AuthenticationException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
