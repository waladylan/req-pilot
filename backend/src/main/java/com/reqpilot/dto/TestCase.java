package com.reqpilot.dto;

import java.util.List;

public record TestCase(
    String id,
    String title,
    String type,
    String priority,
    String actor,
    List<String> preconditions,
    List<TestStep> steps,
    List<String> expectedResults,
    List<String> sourceNodeIds,
    List<String> sourceEdgeIds,
    List<String> tags) {

  public TestCase {
    preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
    steps = steps == null ? List.of() : List.copyOf(steps);
    expectedResults = expectedResults == null ? List.of() : List.copyOf(expectedResults);
    sourceNodeIds = sourceNodeIds == null ? List.of() : List.copyOf(sourceNodeIds);
    sourceEdgeIds = sourceEdgeIds == null ? List.of() : List.copyOf(sourceEdgeIds);
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
