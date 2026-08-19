package com.mkt.risk.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.RiskListType;
import org.junit.jupiter.api.Test;

class RiskListPermissionsTest {

    @Test
    void mapsByListType() {
        assertThat(RiskListPermissions.query(RiskListType.BLACK)).isEqualTo("risk:blacklist:query");
        assertThat(RiskListPermissions.query(RiskListType.WHITE)).isEqualTo("risk:whitelist:query");
        assertThat(RiskListPermissions.add(RiskListType.BLACK)).isEqualTo("risk:blacklist:add");
        assertThat(RiskListPermissions.add(RiskListType.WHITE)).isEqualTo("risk:whitelist:add");
        assertThat(RiskListPermissions.remove(RiskListType.BLACK)).isEqualTo("risk:blacklist:remove");
        assertThat(RiskListPermissions.remove(RiskListType.WHITE)).isEqualTo("risk:whitelist:remove");
        assertThat(RiskListPermissions.importCode(RiskListType.BLACK)).isEqualTo("risk:blacklist:import");
        assertThat(RiskListPermissions.CASE_QUERY).isEqualTo("risk:case:query");
        assertThat(RiskListPermissions.CASE_HANDLE).isEqualTo("risk:case:handle");
        assertThatThrownBy(() -> RiskListPermissions.importCode(RiskListType.WHITE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
