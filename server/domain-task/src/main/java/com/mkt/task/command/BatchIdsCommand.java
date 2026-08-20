package com.mkt.task.command;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchIdsCommand(@NotEmpty @Size(max = 50) List<Long> ids) {}
