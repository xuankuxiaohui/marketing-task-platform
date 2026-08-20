package com.mkt.task.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchItemResponse(long id, boolean success, String errorCode) {

    public static BatchItemResponse ok(long id) {
        return new BatchItemResponse(id, true, null);
    }

    public static BatchItemResponse fail(long id, String errorCode) {
        return new BatchItemResponse(id, false, errorCode);
    }
}
