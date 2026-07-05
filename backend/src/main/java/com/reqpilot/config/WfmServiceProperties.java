package com.reqpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wfm.service")
public record WfmServiceProperties(String baseUrl, int timeoutMs) {

  public WfmServiceProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "http://localhost:8001";
    }
    if (timeoutMs <= 0) {
      timeoutMs = 70000;
    }
  }
}
