package com.reqpilot.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SavedRequirementGenerationResponse(
    RequirementResponse requirement,
    JsonNode wfm,
    FlowchartDto flowchart,
    GenerationMetadataDto metadata) {}
