package com.reqpilot.service;

import com.reqpilot.model.WfmGenerationResult;

public interface WfmGenerator {

  WfmGenerationResult generate(String requirement);
}
