package com.reqpilot.wfm;

import java.util.List;

public class WfmValidationException extends IllegalArgumentException {

  private final List<WfmValidationError> errors;

  public WfmValidationException(List<WfmValidationError> errors) {
    super("Invalid WFM document");
    this.errors = List.copyOf(errors);
  }

  public List<WfmValidationError> errors() {
    return errors;
  }
}
