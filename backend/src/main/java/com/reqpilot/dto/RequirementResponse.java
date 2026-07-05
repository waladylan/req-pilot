package com.reqpilot.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record RequirementResponse(
    UUID id,
    UUID projectId,
    String title,
    String requirementText,
    String status,
    int orderIndex,
    String wfmVersion,
    JsonNode wfm,
    JsonNode flowchart,
    JsonNode metadata,
    JsonNode testCaseSet,
    JsonNode testCaseMetadata,
    Instant testCasesGeneratedAt,
    Instant testCasesUpdatedAt,
    Instant createdAt,
    Instant updatedAt) {}
