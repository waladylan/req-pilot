package com.reqpilot.dto;

import jakarta.validation.Valid;

public record GenerateWfmRequest(
    @Valid RequirementAnalysisDto requirementAnalysis,
    String requirementAnalysisJson) {}
