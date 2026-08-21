package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import org.junit.jupiter.api.Test;

class MetricsGrainTest {

    @Test
    void blankDefaultsToDay() {
        assertThat(MetricsGrain.parse(null)).isEqualTo(MetricsGrain.DAY);
        assertThat(MetricsGrain.parse("")).isEqualTo(MetricsGrain.DAY);
        assertThat(MetricsGrain.parse("week")).isEqualTo(MetricsGrain.WEEK);
    }

    @Test
    void unknownGrainIsParamInvalid() {
        assertThatThrownBy(() -> MetricsGrain.parse("year"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }
}
