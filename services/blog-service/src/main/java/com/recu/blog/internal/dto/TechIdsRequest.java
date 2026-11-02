package com.recu.blog.internal.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record TechIdsRequest(
    @NotNull @Size(min = 1, max = 200) List<@NotNull @Positive Long> techTagIds) {}
