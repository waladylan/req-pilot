package com.reqpilot.wfm;

import java.util.List;

public record WfmQualityReport(
    boolean valid,
    boolean repairable,
    List<String> errors,
    List<String> warnings,
    List<String> repairs) {

  public WfmQualityReport {
    errors = errors == null ? List.of() : List.copyOf(errors);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    repairs = repairs == null ? List.of() : List.copyOf(repairs);
  }

  public static WfmQualityReport valid(List<String> warnings) {
    return new WfmQualityReport(true, false, List.of(), warnings, List.of());
  }
}
