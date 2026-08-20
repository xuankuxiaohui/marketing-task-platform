package com.mkt.reward.domain;

/** Frozen cost snapshot written onto a grant record (R17.9). */
public record PrizeCost(int costFen, Integer faceFen) {

    public static PrizeCost none() {
        return new PrizeCost(0, null);
    }
}
