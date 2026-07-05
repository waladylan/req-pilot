package com.reqpilot.service;

import com.reqpilot.model.GenerationMetadata;
import com.reqpilot.wfm.WfmDocument;
import java.util.List;

public interface WfmRequirementAnalyzer extends RequirementAnalyzer {

  WfmDocument analyzeToWfm(String requirement);

  default RequirementAnalysis analyzeRequirement(String requirement) {
    return new RequirementAnalysis(analyzeToWfm(requirement), GenerationMetadata.ruleBased(List.of()));
  }
}
