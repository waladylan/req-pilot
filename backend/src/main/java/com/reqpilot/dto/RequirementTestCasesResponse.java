package com.reqpilot.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record RequirementTestCasesResponse(
    RequirementResponse requirement, JsonNode testCaseSet, JsonNode metadata) {}
