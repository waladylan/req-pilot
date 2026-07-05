package com.reqpilot.wfm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WfmDocument(
    @NotBlank String schemaVersion,
    @NotBlank String modelType,
    @NotNull @Valid WfmWorkflow workflow,
    @Valid WfmExtensions extensions,
    @NotNull @Valid WfmAst ast) {}
