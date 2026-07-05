package com.reqpilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequirementCreateRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 10000) String requirementText) {}
