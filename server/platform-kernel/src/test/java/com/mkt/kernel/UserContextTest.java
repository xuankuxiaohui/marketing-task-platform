package com.mkt.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UserContextTest {

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void holdsAndClearsPrincipal() {
        UserPrincipal principal = new UserPrincipal(12L, "admin", "root");
        UserContext.set(principal);

        assertThat(UserContext.current()).contains(principal);
        assertThat(UserContext.require()).isEqualTo(principal);

        UserContext.clear();

        assertThat(UserContext.current()).isEmpty();
        assertThatThrownBy(UserContext::require).isInstanceOf(IllegalStateException.class);
    }
}
