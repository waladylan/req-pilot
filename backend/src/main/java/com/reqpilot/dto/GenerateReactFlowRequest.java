package com.reqpilot.dto;

import jakarta.validation.Valid;

public record GenerateReactFlowRequest(@Valid WfmDefinition wfm, String wfmJson) {}
