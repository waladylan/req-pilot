package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MermaidSanitizerTest {

  private final MermaidSanitizer sanitizer = new MermaidSanitizer();

  @Test
  void convertsInvalidNodeIdsIntoSafeMermaidIds() {
    Set<String> usedIds = new HashSet<>();

    assertThat(sanitizer.sanitizeUniqueId("create purchase request", usedIds, 1))
        .isEqualTo("create_purchase_request");
    assertThat(sanitizer.sanitizeUniqueId("123-start", usedIds, 2))
        .isEqualTo("node_123_start");
    assertThat(sanitizer.sanitizeUniqueId("manager.approval", usedIds, 3))
        .isEqualTo("manager_approval");
  }

  @Test
  void ensuresUniqueMermaidIds() {
    Set<String> usedIds = new HashSet<>();

    assertThat(sanitizer.sanitizeUniqueId("manager.approval", usedIds, 1))
        .isEqualTo("manager_approval");
    assertThat(sanitizer.sanitizeUniqueId("manager approval", usedIds, 2))
        .isEqualTo("manager_approval_2");
    assertThat(sanitizer.sanitizeUniqueId("manager_approval", usedIds, 3))
        .isEqualTo("manager_approval_3");
  }

  @Test
  void createsFallbackIdWhenInputIsBlank() {
    Set<String> usedIds = new HashSet<>();

    assertThat(sanitizer.sanitizeUniqueId(" ", usedIds, 4)).isEqualTo("node_4");
  }
}
