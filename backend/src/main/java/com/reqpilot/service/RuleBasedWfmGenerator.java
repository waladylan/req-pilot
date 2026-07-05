package com.reqpilot.service;

import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.model.WfmGenerationResult;
import com.reqpilot.wfm.WfmDocument;
import com.reqpilot.wfm.WfmToFlowchartMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedWfmGenerator implements WfmGenerator {

  private final RuleBasedRequirementAnalyzer ruleBasedRequirementAnalyzer;
  private final WfmToFlowchartMapper flowchartMapper;

  public RuleBasedWfmGenerator(
      RuleBasedRequirementAnalyzer ruleBasedRequirementAnalyzer,
      WfmToFlowchartMapper flowchartMapper) {
    this.ruleBasedRequirementAnalyzer = ruleBasedRequirementAnalyzer;
    this.flowchartMapper = flowchartMapper;
  }

  @Override
  public WfmGenerationResult generate(String requirement) {
    WfmDocument wfm = ruleBasedRequirementAnalyzer.analyzeToWfm(requirement);
    return new WfmGenerationResult(wfm, flowchartMapper.toFlowchart(wfm), GenerationMetadata.ruleBased(List.of()));
  }
}
