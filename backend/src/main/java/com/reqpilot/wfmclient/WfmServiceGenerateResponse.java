package com.reqpilot.wfmclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.reqpilot.model.Flowchart;

public record WfmServiceGenerateResponse(JsonNode wfm, Flowchart flowchart, WfmServiceMetadata metadata) {}
