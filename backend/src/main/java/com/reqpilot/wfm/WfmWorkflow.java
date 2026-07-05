package com.reqpilot.wfm;

import jakarta.validation.constraints.NotBlank;

public record WfmWorkflow(
    @NotBlank String id,
    @NotBlank String title,
    String description,
    String language,
    String domain,
    String sourceRequirement) {}
