package com.reqpilot.service;

import com.reqpilot.model.Flowchart;

public interface RequirementAnalyzer {

  Flowchart analyze(String requirement);
}
