package com.mkt.risk.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiskErrorCodesTest {

    @Test
    void duplicateReturnedIsRegistered() {
        assertThat(RiskErrorCodes.LIST_DUPLICATE_RETURNED.code()).isEqualTo("risk.list.duplicate-returned");
        assertThat(RiskErrorCodes.LIST_DUPLICATE_RETURNED.httpStatus()).isEqualTo(400);
        assertThat(RiskErrorCodes.LIST_DUPLICATE_RETURNED.message()).isNotBlank();
    }
}
