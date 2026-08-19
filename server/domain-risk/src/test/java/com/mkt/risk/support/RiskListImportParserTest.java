package com.mkt.risk.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.risk.domain.RiskDimension;
import org.junit.jupiter.api.Test;

class RiskListImportParserTest {

    @Test
    void parsesValidUserIdsAndCountsInvalid() {
        RiskListImportParser.ParseResult result =
                RiskListImportParser.parse(RiskDimension.USER, "1001\nbad\n\n1002\n0\n-3");
        assertThat(result.values()).containsExactly("1001", "1002");
        assertThat(result.invalid()).isEqualTo(3);
    }

    @Test
    void rejectsOversizedAndControlChars() {
        assertThat(RiskListImportParser.valid(RiskDimension.DEVICE, "ok-device")).isTrue();
        assertThat(RiskListImportParser.valid(RiskDimension.DEVICE, "a".repeat(65))).isFalse();
        assertThat(RiskListImportParser.valid(RiskDimension.IP, "10.0.0.1")).isTrue();
        assertThat(RiskListImportParser.valid(RiskDimension.IP, "10.0.0.256")).isFalse();
        assertThat(RiskListImportParser.valid(RiskDimension.IP, "2001:db8::1")).isTrue();
        assertThat(RiskListImportParser.valid(RiskDimension.IP, "::1")).isTrue();
        assertThat(RiskListImportParser.valid(RiskDimension.IP, "a:b")).isFalse();
        assertThat(RiskListImportParser.valid(RiskDimension.IP, "::::")).isFalse();
        assertThat(RiskListImportParser.valid(RiskDimension.IP, "not-an-ip")).isFalse();
    }

    @Test
    void rejectsNonCanonicalUserAndIpv4() {
        assertThat(RiskListImportParser.valid(RiskDimension.USER, "+123")).isFalse();
        assertThat(RiskListImportParser.valid(RiskDimension.USER, "0123")).isFalse();
        assertThat(RiskListImportParser.valid(RiskDimension.USER, "0")).isFalse();
        assertThat(RiskListImportParser.valid(RiskDimension.IP, "+1.2.3.4")).isFalse();
        assertThat(RiskListImportParser.valid(RiskDimension.IP, "01.02.03.04")).isFalse();
    }

    @Test
    void normalizesIpv6CaseAndIpv4Mapped() {
        assertThat(RiskListImportParser.normalizeOrNull(RiskDimension.IP, "2001:DB8::1"))
                .isEqualTo(RiskListImportParser.normalizeOrNull(RiskDimension.IP, "2001:db8::1"));
        assertThat(RiskListImportParser.normalizeOrNull(RiskDimension.IP, "::ffff:10.0.0.1"))
                .isEqualTo("10.0.0.1");
        assertThat(RiskListImportParser.normalizeOrNull(RiskDimension.DEVICE, "Dev-42")).isEqualTo("dev-42");
    }
}
