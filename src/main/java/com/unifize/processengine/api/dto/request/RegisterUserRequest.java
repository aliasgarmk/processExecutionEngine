package com.unifize.processengine.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RegisterUserRequest(
        @NotBlank String userId,
        @NotBlank String displayName
) {}
