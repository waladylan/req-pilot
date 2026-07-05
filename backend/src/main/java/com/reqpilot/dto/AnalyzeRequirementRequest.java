package com.reqpilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeRequirementRequest(
    @NotBlank(message = "Requirement is required")
        @Size(max = 10000, message = "Requirement must be 10,000 characters or fewer")
        String requirement) {}
