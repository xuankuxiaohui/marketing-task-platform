package com.mkt.task.response;

import java.util.List;

public record PublishCheckResponse(List<PublishCheckError> checkErrors) {}
