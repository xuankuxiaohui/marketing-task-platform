package com.marketing.task.service.platform;

import com.marketing.task.domain.enums.Platform;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PlatformAdapterRegistry {
    private final Map<Platform, PlatformAdapter> adapters;

    public PlatformAdapterRegistry(List<PlatformAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toMap(PlatformAdapter::platform, Function.identity()));
    }

    public PlatformAdapter get(Platform platform) {
        return adapters.getOrDefault(platform, adapters.get(Platform.WEB));
    }
}
