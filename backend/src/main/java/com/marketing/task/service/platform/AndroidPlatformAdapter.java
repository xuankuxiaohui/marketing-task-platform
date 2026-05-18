package com.marketing.task.service.platform;

import com.marketing.task.domain.enums.Platform;
import org.springframework.stereotype.Component;

@Component
public class AndroidPlatformAdapter implements PlatformAdapter {

    @Override
    public Platform platform() {
        return Platform.ANDROID;
    }
}
