package com.reqpilot.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.wfm.WfmDocument;

public final class WfmJson {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private WfmJson() {}

  public static JsonNode from(WfmDocument wfm) {
    return OBJECT_MAPPER.valueToTree(wfm);
  }
}
