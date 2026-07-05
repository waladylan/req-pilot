package com.reqpilot.dto;

import jakarta.validation.Valid;

public record GenerateTestCaseSuiteRequest(@Valid WfmDefinition wfm, String wfmJson) {}
