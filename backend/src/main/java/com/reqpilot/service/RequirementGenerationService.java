package com.reqpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqpilot.model.Flowchart;
import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.model.GeneratedFlow;
import com.reqpilot.model.GeneratedTestCase;
import com.reqpilot.model.GeneratedTestCases;
import com.reqpilot.model.RequirementGeneration;
import com.reqpilot.model.WfmGenerationResult;
import com.reqpilot.repository.RequirementGenerationRepository;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmNormalizer;
import com.reqpilot.wfm.WfmToFlowchartMapper;
import com.reqpilot.wfm.WfmToTestCaseGenerator;
import com.reqpilot.wfm.WfmValidationError;
import com.reqpilot.wfm.WfmValidator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementGenerationService {

  private final WfmGenerator wfmGenerator;
  private final TestCaseGenerator testCaseGenerator;
  private final WfmToFlowchartMapper wfmToFlowchartMapper;
  private final WfmToTestCaseGenerator wfmToTestCaseGenerator;
  private final WfmNormalizer wfmNormalizer;
  private final WfmValidator wfmValidator;
  private final RequirementGenerationRepository repository;
  private final ObjectMapper objectMapper;

  public RequirementGenerationService(
      WfmGenerator wfmGenerator,
      TestCaseGenerator testCaseGenerator,
      WfmToFlowchartMapper wfmToFlowchartMapper,
      WfmToTestCaseGenerator wfmToTestCaseGenerator,
      WfmNormalizer wfmNormalizer,
      WfmValidator wfmValidator,
      RequirementGenerationRepository repository,
      ObjectMapper objectMapper) {
    this.wfmGenerator = wfmGenerator;
    this.testCaseGenerator = testCaseGenerator;
    this.wfmToFlowchartMapper = wfmToFlowchartMapper;
    this.wfmToTestCaseGenerator = wfmToTestCaseGenerator;
    this.wfmNormalizer = wfmNormalizer;
    this.wfmValidator = wfmValidator;
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public GeneratedFlow generateFlow(String requirement) {
    WfmGenerationResult generation = wfmGenerator.generate(requirement);
    Flowchart flowchart = Objects.requireNonNull(generation.flowchart(), "flowchart is required");
    GenerationMetadata metadata = generation.metadata();
    WfmDocument wfmDocument = generation.wfmDocument();
    if (wfmDocument != null) {
      WfmDocument validWfm = wfmValidator.validateOrThrow(wfmDocument);
      metadata = generation.metadata().withValidation(validationWarnings(validWfm), List.of());
      wfmDocument = validWfm;
    }
    saveGeneration(RequirementGeneration.forFlow(requirement, toJson(flowchart), flowchart.mermaid()));
    return new GeneratedFlow(generation.wfm(), wfmDocument, flowchart, metadata);
  }

  @Transactional
  public GeneratedTestCases generateTestCases(String requirement, Flowchart flowchart, WfmDocument wfm) {
    if (wfm != null) {
      return generateTestCasesFromWfm(requirement, wfmNormalizer.normalize(wfm), "WFM");
    }

    if (hasText(requirement)) {
      WfmDocument generatedWfm = wfmGenerator.generate(requirement).wfmDocument();
      if (generatedWfm != null) {
        return generateTestCasesFromWfm(requirement, generatedWfm, "WFM");
      }
    }

    if (flowchart != null) {
      return generateTestCasesFromFlowchart(requirement, flowchart);
    }

    throw new IllegalArgumentException("Provide wfm, requirement, or flowchart to generate test cases");
  }

  private GeneratedTestCases generateTestCasesFromWfm(String requirement, WfmDocument normalizedWfm, String source) {
    WfmDocument validWfm = wfmValidator.validateOrThrow(wfmNormalizer.normalize(normalizedWfm));
    Flowchart sourceFlowchart = wfmToFlowchartMapper.toFlowchart(validWfm);
    String requirementText = resolveRequirementText(requirement, validWfm);
    List<GeneratedTestCase> testCases = wfmToTestCaseGenerator.generate(requirementText, validWfm);
    int pathCount = wfmToTestCaseGenerator.countPaths(validWfm);
    saveGeneration(
        RequirementGeneration.forTestCases(
            requirementText, toJson(sourceFlowchart), sourceFlowchart.mermaid(), toJson(testCases)));
    return new GeneratedTestCases(
        testCases, source, validWfm.workflow().id(), pathCount, validationWarnings(validWfm));
  }

  private GeneratedTestCases generateTestCasesFromFlowchart(String requirement, Flowchart flowchart) {
    String requirementText = hasText(requirement) ? requirement : "Edited flow";
    List<GeneratedTestCase> testCases = testCaseGenerator.generate(requirementText, flowchart);
    saveGeneration(
        RequirementGeneration.forTestCases(
            requirementText, toJson(flowchart), flowchart.mermaid(), toJson(testCases)));
    return new GeneratedTestCases(
        testCases, "FLOWCHART_FALLBACK", "legacy-flowchart", testCases.size(), List.of());
  }

  private List<String> validationWarnings(WfmDocument wfm) {
    return wfmValidator.validate(wfm).warnings().stream()
        .map(WfmValidationError::message)
        .toList();
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize generated artifact", exception);
    }
  }

  private void saveGeneration(RequirementGeneration generation) {
    if (repository != null) {
      repository.save(generation);
    }
  }

  private String resolveRequirementText(String requirement, WfmDocument wfm) {
    if (hasText(requirement)) {
      return requirement;
    }
    if (hasText(wfm.workflow().sourceRequirement())) {
      return wfm.workflow().sourceRequirement();
    }
    return wfm.workflow().title();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
