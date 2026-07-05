package com.reqpilot.dto;

import java.util.List;

public record TestCaseSuite(
    String suiteName,
    String version,
    String sourceWorkflowName,
    List<TestCase> testCases,
    TestCoverage coverage,
    List<String> warnings) {

  public TestCaseSuite {
    testCases = testCases == null ? List.of() : List.copyOf(testCases);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
