package com.reqpilot.wfm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WfmVariable(
    @NotBlank String id,
    @NotBlank String name,
    @NotNull WfmVariableType type,
    String description,
    Boolean required,
    Object defaultValue) {}
