package com.reqpilot.dto;

import java.util.Map;

public record TestStep(
    Integer stepNo,
    String action,
    String actor,
    Map<String, Object> inputData) {

  public TestStep {
    inputData = inputData == null ? Map.of() : Map.copyOf(inputData);
  }
}
