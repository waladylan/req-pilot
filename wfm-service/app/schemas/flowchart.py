from pydantic import BaseModel, Field


class FlowNode(BaseModel):
    id: str
    label: str
    type: str


class FlowEdge(BaseModel):
    id: str
    source: str
    target: str
    label: str | None = None
    type: str = "DEFAULT"


class Flowchart(BaseModel):
    nodes: list[FlowNode] = Field(default_factory=list)
    edges: list[FlowEdge] = Field(default_factory=list)
    mermaid: str
