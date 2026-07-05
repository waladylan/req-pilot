package com.reqpilot.service;

import com.reqpilot.model.Flowchart;
import com.reqpilot.model.GeneratedTestCase;
import java.util.List;

public interface TestCaseGenerator {

  List<GeneratedTestCase> generate(String requirement, Flowchart flowchart);
}
