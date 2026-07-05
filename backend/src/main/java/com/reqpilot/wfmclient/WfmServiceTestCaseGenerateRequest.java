package com.reqpilot.wfmclient;

import com.fasterxml.jackson.databind.JsonNode;

public record WfmServiceTestCaseGenerateRequest(
    JsonNode wfm, WfmServiceTestCaseContext context, WfmServiceTestCaseOptions options) {}
