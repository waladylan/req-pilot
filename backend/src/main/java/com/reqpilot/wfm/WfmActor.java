package com.reqpilot.wfm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WfmActor(@NotBlank String id, @NotBlank String name, @NotNull WfmActorType type) {}
