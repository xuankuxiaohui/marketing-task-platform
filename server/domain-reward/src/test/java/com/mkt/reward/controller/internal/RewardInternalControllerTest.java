package com.mkt.reward.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.reward.application.FulfillmentService;
import com.mkt.reward.command.FulfillmentCallbackCommand;
import com.mkt.reward.response.FulfillmentCallbackResponse;
import org.junit.jupiter.api.Test;

class RewardInternalControllerTest {

    @Test
    void callbackDelegates() {
        FulfillmentService fulfillment = mock(FulfillmentService.class);
        when(fulfillment.callback("ref", "SUCCESS", null))
                .thenReturn(new FulfillmentCallbackResponse("ARRIVED", false));
        RewardInternalController controller = new RewardInternalController(fulfillment);
        assertThat(controller.callback(new FulfillmentCallbackCommand("ref", "SUCCESS", null)).data().fulfillmentStatus())
                .isEqualTo("ARRIVED");
    }
}
