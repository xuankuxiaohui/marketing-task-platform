package com.mkt.ad.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdPositionSaveCommand(
        Long id,
        @NotBlank @Size(min = 4, max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotBlank String form,
        List<String> platforms,
        String status) {}
