package com.reqpilot.service;

import com.reqpilot.ai.AiJsonValidator;
import com.reqpilot.ai.AiProvider;
import com.reqpilot.ai.AiRequest;
import com.reqpilot.ai.AiResponse;
import com.reqpilot.ai.RequirementPrompts;
import com.reqpilot.config.AiProperties;
import com.reqpilot.dto.RequirementAnalysisDto;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class RequirementAnalysisService {

  private final AiProvider aiProvider;
  private final AiProperties aiProperties;
  private final AiJsonValidator jsonValidator;
  private final AiUsageLogService usageLogService;

  public RequirementAnalysisService(
      AiProvider aiProvider,
      AiProperties aiProperties,
      AiJsonValidator jsonValidator,
      AiUsageLogService usageLogService) {
    this.aiProvider = aiProvider;
    this.aiProperties = aiProperties;
    this.jsonValidator = jsonValidator;
    this.usageLogService = usageLogService;
  }

  public RequirementAnalysisDto analyze(String rawRequirement) {
    if (rawRequirement == null || rawRequirement.isBlank()) {
      throw new IllegalArgumentException("Requirement is required");
    }

    AiResponse response =
        aiProvider.generate(
            new AiRequest(
                RequirementPrompts.TASK_TYPE,
                RequirementPrompts.systemPrompt(),
                RequirementPrompts.userPrompt(rawRequirement),
                aiProperties.effectiveModel(),
                aiProperties.effectiveMaxTokens(),
                aiProperties.effectiveTemperature()));
    RequirementAnalysisDto analysis =
        jsonValidator.parseObject(response.content(), RequirementAnalysisDto.class, response.provider());
    usageLogService.log(
        new AiUsageLogEntry(
            null,
            null,
            RequirementPrompts.TASK_TYPE,
            response.provider(),
            response.model(),
            response.promptTokens(),
            response.completionTokens(),
            response.totalTokens(),
            Instant.now()));
    return analysis;
  }
}
