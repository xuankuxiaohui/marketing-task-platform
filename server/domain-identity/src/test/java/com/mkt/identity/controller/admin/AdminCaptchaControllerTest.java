package com.mkt.identity.controller.admin;

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

class AdminCaptchaControllerTest {

    @Test
    void issuesCaptcha() throws Exception {
        CaptchaService captchas = Mockito.mock(CaptchaService.class);
        AuthRateLimiter rates = Mockito.mock(AuthRateLimiter.class);
        when(captchas.issue(anyString())).thenReturn(new CaptchaResponse("id-1", "data:image/png;base64,xx"));
        MockMvcBuilders.standaloneSetup(new AdminCaptchaController(captchas, rates))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/admin/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captchaId").value("id-1"));
    }
}
