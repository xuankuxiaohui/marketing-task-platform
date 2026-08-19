package com.mkt.kernel;

import jakarta.validation.constraints.Min;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(exclude = ErrorMvcAutoConfiguration.class)
public class KernelTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(KernelTestApplication.class, args);
    }

    @Validated
    @RestController
    @RequestMapping("/admin/kernel-probe")
    static class ProbeController {

        @GetMapping("/ok")
        Result<String> ok() {
            return Result.ok("pong");
        }

        @GetMapping("/biz")
        Result<Void> biz() {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }

        @GetMapping("/boom")
        Result<Void> boom() {
            throw new IllegalStateException("jdbc url leaked");
        }

        @GetMapping("/valid")
        Result<Void> valid(@RequestParam @Min(1) int page) {
            return Result.ok();
        }
    }
}
