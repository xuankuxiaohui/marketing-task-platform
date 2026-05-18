package com.marketing.task.service.platform;

import com.marketing.task.domain.enums.Platform;
import org.springframework.stereotype.Component;

@Component
public class AdminPlatformAdapter implements PlatformAdapter {

    @Override
    public Platform platform() {
        return Platform.ADMIN;
    }
}
