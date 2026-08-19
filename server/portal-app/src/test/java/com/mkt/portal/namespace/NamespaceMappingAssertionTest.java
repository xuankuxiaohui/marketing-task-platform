package com.mkt.portal.namespace;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class NamespaceMappingAssertionTest {

    @Test
    void acceptsPortalMapping() throws Exception {
        NamespaceMappingAssertion assertion =
                new NamespaceMappingAssertion(List.of(mapping("/api/common/task")));
        assertThatCode(() -> assertion.run(null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsInternalMapping() throws Exception {
        NamespaceMappingAssertion assertion =
                new NamespaceMappingAssertion(List.of(mapping("/internal/task/callback")));
        assertThatCode(() -> assertion.run(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAdminMapping() throws Exception {
        NamespaceMappingAssertion assertion = new NamespaceMappingAssertion(List.of(mapping("/admin/auth/login")));
        assertThatThrownBy(() -> assertion.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RL-08")
                .hasMessageContaining("/admin/auth/login");
    }

    private static RequestMappingHandlerMapping mapping(String path) throws Exception {
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        RequestMappingInfo info = RequestMappingInfo.paths(path).build();
        HandlerMethod method = new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(info, method));
        return handlerMapping;
    }
}
