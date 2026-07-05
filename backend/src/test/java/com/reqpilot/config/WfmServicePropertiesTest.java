package com.reqpilot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class WfmServicePropertiesTest {

  @Test
  void bindsWorkflowEngineBaseUrlFromEnvironmentBackedProperty() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("wfm.service.base-url", "http://workflow-engine:8001")
            .withProperty("wfm.service.timeout-ms", "70000");

    WfmServiceProperties properties = Binder.get(environment).bind("wfm.service", WfmServiceProperties.class).get();

    assertThat(properties.baseUrl()).isEqualTo("http://workflow-engine:8001");
    assertThat(properties.timeoutMs()).isEqualTo(70000);
  }

  @Test
  void defaultsToLocalWorkflowEngineForDevelopment() {
    WfmServiceProperties properties = new WfmServiceProperties("", 0);

    assertThat(properties.baseUrl()).isEqualTo("http://localhost:8001");
    assertThat(properties.timeoutMs()).isEqualTo(70000);
  }
}
