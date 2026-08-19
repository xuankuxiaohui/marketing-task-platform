package com.mkt.admin.namespace;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Fail-fast if any registered MVC path leaves the admin namespace (design §2.3.3). */
@Component
public class NamespaceMappingAssertion implements ApplicationRunner {

    private final List<RequestMappingHandlerMapping> handlerMappings;

    public NamespaceMappingAssertion(List<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            mapping.getHandlerMethods().forEach((info, method) -> {
                for (String pattern : patternsOf(info)) {
                    if (!NamespacePrefixes.allows(pattern)) {
                        throw new IllegalStateException(
                                "RL-08 namespace violation: " + pattern + " -> " + method);
                    }
                }
            });
        }
    }

    private static Iterable<String> patternsOf(RequestMappingInfo info) {
        return info.getPatternValues();
    }
}
