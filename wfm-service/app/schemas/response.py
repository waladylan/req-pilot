from typing import Any

from pydantic import BaseModel, Field

from app.schemas.flowchart import Flowchart
from app.schemas.test_case import GenerateTestCasesMetadata, TestCaseSet
from app.schemas.wfm import WfmDocument


class GenerateWfmMetadata(BaseModel):
    model: str | None = None
    promptVersion: str
    generationMode: str | None = None
    wfmVersion: str | None = None
    wfmSource: str | None = None
    flowchartSource: str | None = None
    validationStatus: str = "PASSED"
    canonicalizationStatus: str | None = None
    normalizationStatus: str | None = None
    mappingStatus: str | None = None
    warnings: list[str] = Field(default_factory=list)


class GenerateWfmResponse(BaseModel):
    wfm: WfmDocument
    metadata: GenerateWfmMetadata


class GenerateWorkflowResponse(BaseModel):
    wfm: WfmDocument | dict[str, Any]
    flowchart: Flowchart
    metadata: GenerateWfmMetadata


class GenerateTestCasesResponse(BaseModel):
    testCaseSet: TestCaseSet
    metadata: GenerateTestCasesMetadata
