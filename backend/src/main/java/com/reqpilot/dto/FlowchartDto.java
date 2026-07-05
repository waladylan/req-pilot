package com.reqpilot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record FlowchartDto(
    @NotEmpty List<@Valid FlowNodeDto> nodes,
    @NotEmpty List<@Valid FlowEdgeDto> edges,
    @NotBlank String mermaid) {}
