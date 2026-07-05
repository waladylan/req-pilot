package com.reqpilot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.reqpilot.config.AnalyzerMode;
import com.reqpilot.config.RequirementAnalyzerProperties;
import com.reqpilot.model.Flowchart;
import com.reqpilot.wfm.WfmDocument;
import org.junit.jupiter.api.Test;

class ConfigurableRequirementAnalyzerTest {

  @Test
  void usesRuleBasedAnalyzerWhenRuleBasedModeIsConfigured() {
    StubAiAnalyzer aiAnalyzer = new StubAiAnalyzer(null);
    RuleBasedRequirementAnalyzer ruleBasedAnalyzer = new RuleBasedRequirementAnalyzer();
    ConfigurableRequirementAnalyzer analyzer =
        new ConfigurableRequirementAnalyzer(
            aiAnalyzer,
            new RequirementAnalyzerProperties(AnalyzerMode.RULE_BASED),
            ruleBasedAnalyzer);

    assertThat(analyzer.analyzeToWfm("Feature: Delete Product\nStart\nUser confirms delete\nEnd"))
        .satisfies(
            wfm -> {
              assertThat(wfm.schemaVersion()).isEqualTo("1.0");
              assertThat(wfm.modelType()).isEqualTo("WORKFLOW_AST");
              assertThat(wfm.ast().nodes()).isNotEmpty();
            });
    assertThat(aiAnalyzer.calls).isZero();
  }

  @Test
  void delegatesToAiAnalyzerWhenAiModeIsConfigured() {
    String requirement = "Feature: Delete Product\nStart\nUser confirms delete\nEnd";
    WfmDocument aiWfm = new RuleBasedRequirementAnalyzer().analyzeToWfm(requirement);
    StubAiAnalyzer aiAnalyzer = new StubAiAnalyzer(aiWfm);

    ConfigurableRequirementAnalyzer analyzer =
        new ConfigurableRequirementAnalyzer(
            aiAnalyzer,
            new RequirementAnalyzerProperties(AnalyzerMode.AI),
            new RuleBasedRequirementAnalyzer());

    assertThat(analyzer.analyzeToWfm(requirement)).isSameAs(aiWfm);
    assertThat(aiAnalyzer.calls).isEqualTo(1);
  }

  private static final class StubAiAnalyzer implements WfmRequirementAnalyzer {

    private final WfmDocument wfm;
    private int calls;

    private StubAiAnalyzer(WfmDocument wfm) {
      this.wfm = wfm;
    }

    @Override
    public Flowchart analyze(String requirement) {
      throw new UnsupportedOperationException("Not needed in this test");
    }

    @Override
    public WfmDocument analyzeToWfm(String requirement) {
      calls++;
      return wfm;
    }
  }
}
