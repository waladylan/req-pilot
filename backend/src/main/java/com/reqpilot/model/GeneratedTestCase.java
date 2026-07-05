package com.reqpilot.model;

import java.util.List;
import java.util.Objects;

public record GeneratedTestCase(
    String id,
    String title,
    String preconditions,
    List<String> steps,
    String expectedResult,
    TestCasePriority priority) {

  public GeneratedTestCase {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(title, "title is required");
    Objects.requireNonNull(preconditions, "preconditions are required");
    steps = List.copyOf(Objects.requireNonNull(steps, "steps are required"));
    Objects.requireNonNull(expectedResult, "expectedResult is required");
    Objects.requireNonNull(priority, "priority is required");
  }
}
