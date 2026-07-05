package com.reqpilot.wfm;

import com.reqpilot.dto.WfmDefinition;

public record WfmNormalizationResult(WfmDefinition wfm, WfmQualityReport report) {}
