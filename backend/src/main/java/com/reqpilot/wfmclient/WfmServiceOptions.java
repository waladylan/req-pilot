package com.reqpilot.wfmclient;

public record WfmServiceOptions(String generationMode, String wfmVersion, String model, Double temperature) {

  public WfmServiceOptions(String model, Double temperature) {
    this(null, null, model, temperature);
  }

  public WfmServiceOptions(String generationMode, String model, Double temperature) {
    this(generationMode, null, model, temperature);
  }
}
