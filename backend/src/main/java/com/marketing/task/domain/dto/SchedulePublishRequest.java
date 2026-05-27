package com.marketing.task.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchedulePublishRequest {
    @NotNull
    private LocalDateTime publishAt;
}
