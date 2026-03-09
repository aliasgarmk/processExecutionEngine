package com.unifize.processengine.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OpenStepRequest(@NotBlank String actorId) {}
