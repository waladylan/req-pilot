package com.reqpilot.wfmclient;

public record WfmServiceTestCaseOptions(String strategy, int maxCases, boolean includeNegativeCases) {

  public WfmServiceTestCaseOptions {
    if (strategy == null || strategy.isBlank()) {
      strategy = "PATH_COVERAGE";
    }
    if (maxCases <= 0) {
      maxCases = 30;
    }
  }

  public WfmServiceTestCaseOptions() {
    this("PATH_COVERAGE", 30, true);
  }
}
