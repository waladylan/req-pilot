package com.reqpilot.wfmclient;

import com.fasterxml.jackson.databind.JsonNode;

public record WfmServiceTestCaseGenerateResponse(JsonNode testCaseSet, JsonNode metadata) {}
