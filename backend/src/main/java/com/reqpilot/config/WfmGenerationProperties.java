package com.reqpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "wfm.generation")
public record WfmGenerationProperties(WfmGenerationMode mode, boolean allowFallback, String version) {

  @ConstructorBinding
  public WfmGenerationProperties {
    if (mode == null) {
      mode = WfmGenerationMode.RULE_BASED;
    }
    if (version == null || version.isBlank()) {
      version = "2.0";
    }
  }

  public WfmGenerationProperties(WfmGenerationMode mode, boolean allowFallback) {
    this(mode, allowFallback, "2.0");
  }
}
