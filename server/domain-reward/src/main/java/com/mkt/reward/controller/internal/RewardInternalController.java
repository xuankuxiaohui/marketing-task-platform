package com.mkt.reward.controller.internal;

import com.mkt.kernel.Result;
import com.mkt.reward.application.FulfillmentService;
import com.mkt.reward.command.FulfillmentCallbackCommand;
import com.mkt.reward.response.FulfillmentCallbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/reward")
@Tag(name = "internal-reward")
public class RewardInternalController {

    private final FulfillmentService fulfillment;

    public RewardInternalController(FulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    @PostMapping("/fulfillment/callback")
    @Operation(summary = "第三方履约回执", description = "HMAC 四头；SUCCESS→ARRIVED，FAILED 计次")
    public Result<FulfillmentCallbackResponse> callback(@Valid @RequestBody FulfillmentCallbackCommand command) {
        return Result.ok(fulfillment.callback(command.fulfillmentRef(), command.result(), command.failReason()));
    }
}
