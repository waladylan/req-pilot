package com.reqpilot.dto;

import com.reqpilot.model.TestCasePriority;
import java.util.List;

public record TestCaseDto(
    String id,
    String title,
    String preconditions,
    List<String> steps,
    String expectedResult,
    TestCasePriority priority) {}
