package com.marketing.task.security;

import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;

public class AuthenticationException extends BusinessException {
    public AuthenticationException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
