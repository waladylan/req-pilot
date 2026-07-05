package com.reqpilot.service;

import org.springframework.stereotype.Service;

@Service
public class NoopAiUsageLogService implements AiUsageLogService {

  @Override
  public void log(AiUsageLogEntry entry) {
    // TODO: Persist AI usage logs once user/project ownership is introduced.
  }
}
