package com.reqpilot.dto;

import com.reqpilot.wfm.WfmDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record GenerateTestCasesRequest(
    @Size(max = 10000, message = "Requirement must be 10,000 characters or fewer")
        String requirement,
    @Valid FlowchartDto flowchart,
    @Valid WfmDocument wfm) {}
