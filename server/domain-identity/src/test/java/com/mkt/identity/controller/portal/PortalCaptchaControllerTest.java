package com.mkt.identity.controller.portal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.AuthRateLimiter;
import com.mkt.identity.application.CaptchaService;
import com.mkt.identity.response.CaptchaResponse;
import com.mkt.kernel.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PortalCaptchaControllerTest {

    @Test
    void issuesCaptcha() throws Exception {
        CaptchaService captchas = Mockito.mock(CaptchaService.class);
        AuthRateLimiter rates = Mockito.mock(AuthRateLimiter.class);
        when(captchas.issue(anyString())).thenReturn(new CaptchaResponse("id-2", "data:image/png;base64,yy"));
        MockMvcBuilders.standaloneSetup(new PortalCaptchaController(captchas, rates))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/common/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captchaId").value("id-2"));
    }
}
