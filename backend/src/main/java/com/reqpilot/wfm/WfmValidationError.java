package com.reqpilot.wfm;

public record WfmValidationError(
    WfmValidationSeverity severity, String code, String path, String message) {}
