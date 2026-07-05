package com.reqpilot.service;

import com.reqpilot.config.AnalyzerMode;
import com.reqpilot.config.RequirementAnalyzerProperties;
import com.reqpilot.model.Flowchart;
import com.reqpilot.wfm.WfmDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class ConfigurableRequirementAnalyzer implements WfmRequirementAnalyzer {

  private final WfmRequirementAnalyzer aiAnalyzer;
  private final RequirementAnalyzerProperties analyzerProperties;
  private final RuleBasedRequirementAnalyzer ruleBasedAnalyzer;

  public ConfigurableRequirementAnalyzer(
      @Qualifier("aiRequirementAnalyzer") WfmRequirementAnalyzer aiAnalyzer,
      RequirementAnalyzerProperties analyzerProperties,
      RuleBasedRequirementAnalyzer ruleBasedAnalyzer) {
    this.aiAnalyzer = aiAnalyzer;
    this.analyzerProperties = analyzerProperties;
    this.ruleBasedAnalyzer = ruleBasedAnalyzer;
  }

  @Override
  public Flowchart analyze(String requirement) {
    return activeAnalyzer().analyze(requirement);
  }

  @Override
  public WfmDocument analyzeToWfm(String requirement) {
    return activeAnalyzer().analyzeToWfm(requirement);
  }

  @Override
  public RequirementAnalysis analyzeRequirement(String requirement) {
    return activeAnalyzer().analyzeRequirement(requirement);
  }

  private WfmRequirementAnalyzer activeAnalyzer() {
    if (analyzerProperties.mode() == AnalyzerMode.AI) {
      return aiAnalyzer;
    }
    return ruleBasedAnalyzer;
  }
}
