package com.reqpilot.model;

import java.util.List;

public record GeneratedTestCases(
    List<GeneratedTestCase> testCases,
    String source,
    String workflowId,
    int pathCount,
    List<String> warnings) {

  public GeneratedTestCases {
    testCases = List.copyOf(testCases == null ? List.of() : testCases);
    warnings = List.copyOf(warnings == null ? List.of() : warnings);
  }
}
