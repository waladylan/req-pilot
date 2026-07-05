package com.reqpilot.wfm;

import java.util.List;

public record WfmValidationResult(List<WfmValidationError> errors, List<WfmValidationError> warnings) {

  public WfmValidationResult(List<WfmValidationError> findings) {
    this(
        splitBySeverity(findings, WfmValidationSeverity.ERROR),
        splitBySeverity(findings, WfmValidationSeverity.WARNING));
  }

  public WfmValidationResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public boolean valid() {
    return errors.isEmpty();
  }

  public List<WfmValidationError> findings() {
    return java.util.stream.Stream.concat(errors.stream(), warnings.stream()).toList();
  }

  private static List<WfmValidationError> splitBySeverity(
      List<WfmValidationError> findings, WfmValidationSeverity severity) {
    if (findings == null) {
      return List.of();
    }
    return findings.stream().filter((finding) -> finding.severity() == severity).toList();
  }
}
