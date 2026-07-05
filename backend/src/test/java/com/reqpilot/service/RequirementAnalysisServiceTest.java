package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.ai.AiJsonValidator;
import com.reqpilot.ai.AiProvider;
import com.reqpilot.ai.AiRequest;
import com.reqpilot.ai.AiResponse;
import com.reqpilot.ai.RequirementPrompts;
import com.reqpilot.config.AiProperties;
import com.reqpilot.dto.RequirementAnalysisDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequirementAnalysisServiceTest {

  @Test
  void rejectsBlankRequirement() {
    RequirementAnalysisService service =
        new RequirementAnalysisService(
            new StubAiProvider(), aiProperties(), new AiJsonValidator(new ObjectMapper()), new StubUsageLogService());

    assertThatThrownBy(() -> service.analyze(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Requirement is required");
  }

  @Test
  void callsAiProviderWithRequirementAnalysisTaskType() {
    StubAiProvider provider = new StubAiProvider();
    StubUsageLogService usageLogService = new StubUsageLogService();
    RequirementAnalysisService service =
        new RequirementAnalysisService(
            provider, aiProperties(), new AiJsonValidator(new ObjectMapper()), usageLogService);

    RequirementAnalysisDto analysis =
        service.analyze("User can create a purchase request. Manager approves.");

    assertThat(provider.lastRequest.taskType()).isEqualTo(RequirementPrompts.TASK_TYPE);
    assertThat(provider.lastRequest.systemPrompt()).contains("senior business analyst");
    assertThat(provider.lastRequest.userPrompt()).contains("purchase request");
    assertThat(analysis.summary()).isEqualTo("Purchase request approval");
    assertThat(usageLogService.loggedEntry.taskType()).isEqualTo(RequirementPrompts.TASK_TYPE);
    assertThat(usageLogService.loggedEntry.totalTokens()).isEqualTo(30);
  }

  private AiProperties aiProperties() {
    return new AiProperties(
        "openrouter",
        "deepseek/deepseek-chat",
        "",
        60000,
        4096,
        0.2,
        true,
        new AiProperties.Cache(true, 30),
        "requirement-to-wfm-v1",
        new AiProperties.OpenRouter(
            "https://openrouter.ai/api/v1",
            "test-key",
            "deepseek/deepseek-chat",
            List.of("qwen/qwen3-32b:nitro", "deepseek/deepseek-chat-v3-0324"),
            0.2,
            4096,
            60));
  }

  private static final class StubAiProvider implements AiProvider {

    private AiRequest lastRequest;

    @Override
    public AiResponse generate(AiRequest request) {
      lastRequest = request;
      return new AiResponse(
          """
          {
            "summary": "Purchase request approval",
            "actors": ["Requester", "Manager"],
            "modules": [],
            "assumptions": [],
            "openQuestions": [],
            "riskLevel": "MEDIUM"
          }
          """,
          "OPENROUTER",
          "deepseek/deepseek-chat",
          10,
          20,
          30,
          "response-id");
    }
  }

  private static final class StubUsageLogService implements AiUsageLogService {

    private AiUsageLogEntry loggedEntry;

    @Override
    public void log(AiUsageLogEntry entry) {
      loggedEntry = entry;
    }
  }
}
