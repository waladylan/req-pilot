package com.reqpilot.wfm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WfmNodeKindDefinition(
    @NotBlank String kind,
    @NotNull WfmNodeRole extendsRole,
    @NotBlank String label,
    String description,
    String namespace) {}
