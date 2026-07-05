from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class WfmV2PermissiveModel(BaseModel):
    model_config = ConfigDict(extra="allow", populate_by_name=True)


class WfmV2Node(WfmV2PermissiveModel):
    id: str = Field(min_length=1)
    kind: str = Field(min_length=1)
    name: str = Field(min_length=1)
    description: str | None = None
    actor: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)


class WfmV2Transition(WfmV2PermissiveModel):
    id: str = Field(min_length=1)
    source: str = Field(min_length=1)
    target: str = Field(min_length=1)
    label: str | None = None
    condition: str | None = None
    outcome: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)


class WfmV2Metadata(WfmV2PermissiveModel):
    source: str | None = None
    language: str | None = None
    warnings: list[str] = Field(default_factory=list)


class WfmV2(WfmV2PermissiveModel):
    wfmVersion: str = Field(min_length=1)
    workflowId: str = Field(min_length=1)
    workflowName: str = Field(min_length=1)
    description: str | None = None
    direction: str = "LR"
    nodes: list[WfmV2Node] = Field(default_factory=list)
    transitions: list[WfmV2Transition] = Field(default_factory=list)
    metadata: WfmV2Metadata = Field(default_factory=WfmV2Metadata)


class WfmValidationIssue(BaseModel):
    code: str
    message: str
    path: str


class WfmValidationResult(BaseModel):
    valid: bool
    errors: list[WfmValidationIssue] = Field(default_factory=list)
    warnings: list[WfmValidationIssue] = Field(default_factory=list)
