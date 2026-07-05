package com.reqpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "requirement.analyzer")
public record RequirementAnalyzerProperties(AnalyzerMode mode) {

  public RequirementAnalyzerProperties {
    if (mode == null) {
      mode = AnalyzerMode.RULE_BASED;
    }
  }
}
