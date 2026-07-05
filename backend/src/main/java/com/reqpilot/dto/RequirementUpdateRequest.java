package com.reqpilot.dto;

import jakarta.validation.constraints.Size;

public record RequirementUpdateRequest(
    @Size(max = 255) String title,
    @Size(max = 10000) String requirementText,
    String status,
    Integer orderIndex) {}
