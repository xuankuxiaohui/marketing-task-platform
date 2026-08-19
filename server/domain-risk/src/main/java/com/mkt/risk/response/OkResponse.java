package com.mkt.risk.response;

public record OkResponse(boolean ok) {

    public static OkResponse yes() {
        return new OkResponse(true);
    }
}
