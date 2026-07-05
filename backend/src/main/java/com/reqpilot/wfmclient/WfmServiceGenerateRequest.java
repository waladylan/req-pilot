package com.reqpilot.wfmclient;

import jakarta.validation.constraints.NotBlank;

public record WfmServiceGenerateRequest(
    @NotBlank String requirement, WfmServiceContext context, WfmServiceOptions options) {}
