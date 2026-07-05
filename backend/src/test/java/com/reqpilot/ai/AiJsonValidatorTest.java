package com.reqpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiJsonValidatorTest {

  private final AiJsonValidator validator = new AiJsonValidator(new ObjectMapper());

  @Test
  void parsesValidJsonObject() {
    JsonNode node = validator.parseObject("{\"summary\":\"ok\"}", "OPENROUTER");

    assertThat(node.path("summary").asText()).isEqualTo("ok");
  }

  @Test
  void stripsMarkdownCodeFenceAndParsesJson() {
    JsonNode node = validator.parseObject("```json\n{\"summary\":\"ok\"}\n```", "OPENROUTER");

    assertThat(node.path("summary").asText()).isEqualTo("ok");
  }

  @Test
  void rejectsInvalidJson() {
    assertThatThrownBy(() -> validator.parseObject("not-json", "OPENROUTER"))
        .isInstanceOf(AiProviderException.class)
        .hasMessageContaining("AI response does not contain a JSON object");
  }
}
