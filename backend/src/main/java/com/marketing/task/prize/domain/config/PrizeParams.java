package com.marketing.task.prize.domain.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PointParams.class, name = "POINT"),
    @JsonSubTypes.Type(value = CouponParams.class, name = "COUPON"),
    @JsonSubTypes.Type(value = BadgeParams.class, name = "BADGE"),
    @JsonSubTypes.Type(value = PhysicalParams.class, name = "PHYSICAL"),
    @JsonSubTypes.Type(value = MembershipParams.class, name = "MEMBERSHIP")
})
public interface PrizeParams {
}
