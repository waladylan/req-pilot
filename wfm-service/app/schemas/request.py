from pydantic import BaseModel, Field
from typing import Any


class GenerateWfmContext(BaseModel):
    projectId: str | None = None
    requirementId: str | None = None
    language: str | None = None
    domain: str | None = None


class GenerateWfmOptions(BaseModel):
    generationMode: str | None = None
    wfmVersion: str | None = None
    model: str | None = None
    temperature: float | None = Field(default=None, ge=0)


class GenerateWfmRequest(BaseModel):
    requirement: str = Field(min_length=1, max_length=10000)
    context: GenerateWfmContext | None = None
    options: GenerateWfmOptions | None = None


class GenerateTestCasesContext(BaseModel):
    projectId: str | None = None
    requirementId: str | None = None
    language: str | None = None


class GenerateTestCasesOptions(BaseModel):
    strategy: str = "PATH_COVERAGE"
    maxCases: int = Field(default=30, ge=1, le=100)
    includeNegativeCases: bool = True


class GenerateTestCasesRequest(BaseModel):
    wfm: dict[str, Any]
    context: GenerateTestCasesContext | None = None
    options: GenerateTestCasesOptions = Field(default_factory=GenerateTestCasesOptions)
