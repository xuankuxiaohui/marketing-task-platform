package com.mkt.risk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.RiskListType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RiskListKeysTest {

    @Test
    void keyFormatMatchesSchema() {
        assertThat(RiskListKeys.of(RiskDimension.USER, RiskListType.BLACK, "12"))
                .isEqualTo("risk:list:USER:BLACK:12");
    }

    @Test
    void entryRejectsBlankValue() {
        assertThatThrownBy(() -> new ListEntry(RiskDimension.IP, RiskListType.BLACK, " ", null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ListEntry(RiskDimension.IP, RiskListType.BLACK, "1.1.1.1", Instant.EPOCH, false)
                        .effectiveAt(Instant.EPOCH))
                .isFalse();
    }
}
