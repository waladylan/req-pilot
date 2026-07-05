package com.reqpilot.dto;

import java.util.List;

public record TestCoverage(
    Integer nodeCount,
    Integer edgeCount,
    List<String> coveredNodeIds,
    List<String> coveredEdgeIds,
    List<String> uncoveredNodeIds,
    List<String> uncoveredEdgeIds) {

  public TestCoverage {
    coveredNodeIds = coveredNodeIds == null ? List.of() : List.copyOf(coveredNodeIds);
    coveredEdgeIds = coveredEdgeIds == null ? List.of() : List.copyOf(coveredEdgeIds);
    uncoveredNodeIds = uncoveredNodeIds == null ? List.of() : List.copyOf(uncoveredNodeIds);
    uncoveredEdgeIds = uncoveredEdgeIds == null ? List.of() : List.copyOf(uncoveredEdgeIds);
  }
}
