package com.mkt.activity.response;

public record OkResponse(boolean ok) {

    public static OkResponse yes() {
        return new OkResponse(true);
    }
}
