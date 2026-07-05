package com.reqpilot.service;

import com.reqpilot.model.WfmGenerationResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class ConfigurableWfmGenerator implements WfmGenerator {

  private final WfmGenerator pythonWorkflowGenerator;

  public ConfigurableWfmGenerator(
      @Qualifier("pythonAiWfmGenerator") WfmGenerator pythonWorkflowGenerator) {
    this.pythonWorkflowGenerator = pythonWorkflowGenerator;
  }

  @Override
  public WfmGenerationResult generate(String requirement) {
    return pythonWorkflowGenerator.generate(requirement);
  }
}
