package com.reqpilot.wfm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WfmTransitionKindDefinition(
    @NotBlank String kind,
    @NotNull WfmTransitionSemantic extendsSemantic,
    @NotBlank String label,
    String description,
    String namespace) {}
