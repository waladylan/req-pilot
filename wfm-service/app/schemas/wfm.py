from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class PermissiveModel(BaseModel):
    model_config = ConfigDict(extra="allow", populate_by_name=True)


class WfmWorkflow(PermissiveModel):
    id: str | None = None
    title: str | None = None
    description: str | None = None
    language: str | None = None
    domain: str | None = None
    sourceRequirement: str | None = None


class WfmExtensions(PermissiveModel):
    nodeKinds: list[dict[str, Any]] = Field(default_factory=list)
    transitionKinds: list[dict[str, Any]] = Field(default_factory=list)


class WfmActor(PermissiveModel):
    id: str | None = None
    name: str | None = None
    type: str | None = None


class WfmVariable(PermissiveModel):
    id: str | None = None
    name: str | None = None
    type: str | None = None
    description: str | None = None
    required: bool | None = None
    defaultValue: Any | None = None


class WfmNode(PermissiveModel):
    id: str | None = None
    role: str | None = None
    kind: str | None = None
    title: str | None = None
    description: str | None = None
    actorId: str | None = None
    tags: list[str] = Field(default_factory=list)
    data: dict[str, Any] = Field(default_factory=dict)


class WfmTransition(PermissiveModel):
    id: str | None = None
    from_: str | None = Field(default=None, alias="from")
    to: str | None = None
    semantic: str | None = None
    kind: str | None = None
    condition: str | None = None
    description: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)


class WfmAnnotation(PermissiveModel):
    id: str | None = None
    target: dict[str, Any] | None = None
    severity: str | None = None
    message: str | None = None


class WfmAst(PermissiveModel):
    actors: list[WfmActor] = Field(default_factory=list)
    variables: list[WfmVariable] = Field(default_factory=list)
    nodes: list[WfmNode] = Field(default_factory=list)
    transitions: list[WfmTransition] = Field(default_factory=list)
    annotations: list[WfmAnnotation] = Field(default_factory=list)


class WfmDocument(PermissiveModel):
    schemaVersion: str | None = None
    modelType: str | None = None
    workflow: WfmWorkflow | None = None
    extensions: WfmExtensions | None = None
    ast: WfmAst | None = None
