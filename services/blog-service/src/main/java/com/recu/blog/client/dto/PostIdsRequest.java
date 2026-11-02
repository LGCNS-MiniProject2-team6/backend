package com.recu.blog.client.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostIdsRequest(
    @NotNull @Size(min = 1, max = 200) List<@NotNull @Positive Long> postIds) {}
