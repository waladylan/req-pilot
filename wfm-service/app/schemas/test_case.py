from typing import Any, ClassVar

from pydantic import BaseModel, Field

__test__ = False


class TestCaseSourcePath(BaseModel):
    __test__: ClassVar[bool] = False

    nodeIds: list[str] = Field(default_factory=list)
    transitionIds: list[str] = Field(default_factory=list)


class TestCaseStep(BaseModel):
    __test__: ClassVar[bool] = False

    stepNo: int
    action: str
    expectedResult: str


class GeneratedTestCase(BaseModel):
    id: str
    title: str
    type: str = "FUNCTIONAL"
    priority: str
    sourcePath: TestCaseSourcePath
    preconditions: list[str] = Field(default_factory=list)
    steps: list[TestCaseStep] = Field(default_factory=list)
    expectedResult: str
    testData: dict[str, Any] = Field(default_factory=dict)
    tags: list[str] = Field(default_factory=list)


class TestCaseCoverage(BaseModel):
    __test__: ClassVar[bool] = False

    nodeCount: int
    transitionCount: int
    coveredNodeIds: list[str] = Field(default_factory=list)
    coveredTransitionIds: list[str] = Field(default_factory=list)
    uncoveredNodeIds: list[str] = Field(default_factory=list)
    uncoveredTransitionIds: list[str] = Field(default_factory=list)
    branchCoverage: list[dict[str, Any]] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


class TestCaseSetMetadata(BaseModel):
    __test__: ClassVar[bool] = False

    generator: str = "python-wfm-v2-test-case-generator"
    strategy: str = "PATH_COVERAGE"
    generationMode: str = "RULE_BASED"
    warnings: list[str] = Field(default_factory=list)


class TestCaseSet(BaseModel):
    __test__: ClassVar[bool] = False

    testCaseVersion: str = "1.0"
    sourceWfmVersion: str = "2.0"
    workflowId: str
    workflowName: str
    testCases: list[GeneratedTestCase] = Field(default_factory=list)
    coverage: TestCaseCoverage
    metadata: TestCaseSetMetadata


class GenerateTestCasesMetadata(BaseModel):
    sourceWfmVersion: str = "2.0"
    generator: str = "python-wfm-v2-test-case-generator"
    strategy: str = "PATH_COVERAGE"
    generationStatus: str = "PASSED"
    warnings: list[str] = Field(default_factory=list)
