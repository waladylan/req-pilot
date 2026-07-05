package com.reqpilot.wfmclient;

public record WfmServiceContext(String projectId, String requirementId, String language, String domain) {

  public WfmServiceContext(String projectId, String language, String domain) {
    this(projectId, null, language, domain);
  }
}
