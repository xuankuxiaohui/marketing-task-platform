package com.mkt.tracking.command;

import com.mkt.tracking.domain.PropSchemaItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TrackMetadataSaveCommand(
        @NotBlank @Size(max = 64) String eventCode,
        @NotBlank @Size(max = 64) String name,
        List<PropSchemaItem> propSchema,
        @NotBlank @Size(max = 16) String status,
        @Size(max = 32) String owner,
        @Size(max = 255) String remark) {}
