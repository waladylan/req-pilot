package com.reqpilot.dto;

import java.util.List;

public record GenerateTestCasesResponse(
    List<TestCaseDto> testCases, TestCaseGenerationMetadataDto metadata) {}
