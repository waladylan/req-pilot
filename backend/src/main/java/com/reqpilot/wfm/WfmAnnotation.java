package com.reqpilot.wfm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WfmAnnotation(
    @NotBlank String id,
    @NotNull WfmAnnotationTarget targetType,
    String targetId,
    @NotBlank String text,
    WfmAnnotationSeverity severity) {}
