package com.reqpilot.dto;

public record GenerateFlowchartOptions(
    Boolean includeDebug,
    Boolean includeRequirementAnalysis,
    Boolean includeWfm) {

  public boolean debugEnabled() {
    return Boolean.TRUE.equals(includeDebug);
  }

  public boolean requirementAnalysisDebugEnabled() {
    return debugEnabled() && (includeRequirementAnalysis == null || Boolean.TRUE.equals(includeRequirementAnalysis));
  }

  public boolean wfmDebugEnabled() {
    return debugEnabled() && (includeWfm == null || Boolean.TRUE.equals(includeWfm));
  }
}
