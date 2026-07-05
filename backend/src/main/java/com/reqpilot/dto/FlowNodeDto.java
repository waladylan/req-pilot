package com.reqpilot.dto;

import com.reqpilot.model.FlowNodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FlowNodeDto(
    @NotBlank String id, @NotBlank String label, @NotNull FlowNodeType type) {}
