from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.config import Settings
from app.schemas.request import GenerateTestCasesRequest, GenerateWfmRequest
from app.schemas.response import GenerateTestCasesResponse, GenerateWfmResponse, GenerateWorkflowResponse
from app.services.flowchart_mapper import FlowchartMapper
from app.services.json_parser import JsonParser
from app.services.llm_client import LlmClient
from app.services.prompt_builder import PromptBuilder
from app.services.prompt_builder_v2 import PromptBuilderV2
from app.services.rule_based_generator import RuleBasedGenerator
from app.services.rule_based_generator_v2 import RuleBasedGeneratorV2
from app.services.test_case_generator import WfmV2TestCaseGenerator
from app.services.wfm_generator import WfmGenerator, WfmServiceError
from app.services.wfm_normalizer import WfmNormalizer
from app.services.wfm_validator import WfmValidator
from app.services.wfm_v2_canonicalizer import WfmV2Canonicalizer
from app.services.wfm_v2_generator import WfmV2Generator
from app.services.wfm_v2_validator import WfmV2Validator
from app.services.workflow_generator import WorkflowGenerator

settings = Settings.from_environment()
validator = WfmValidator()
generator = WfmGenerator(
    prompt_builder=PromptBuilder(settings.prompt_version),
    llm_client=LlmClient(settings),
    json_parser=JsonParser(),
    validator=validator,
    settings=settings,
)
wfm_v2_generator = WfmV2Generator(
    prompt_builder=PromptBuilderV2(),
    llm_client=LlmClient(settings),
    json_parser=JsonParser(),
    rule_based_generator=RuleBasedGeneratorV2(),
    canonicalizer=WfmV2Canonicalizer(),
    validator=WfmV2Validator(),
    settings=settings,
)
workflow_generator = WorkflowGenerator(
    wfm_generator=generator,
    rule_based_generator=RuleBasedGenerator(),
    wfm_v2_generator=wfm_v2_generator,
    normalizer=WfmNormalizer(),
    validator=validator,
    flowchart_mapper=FlowchartMapper(),
    settings=settings,
)
test_case_generator = WfmV2TestCaseGenerator(WfmV2Validator())

app = FastAPI(title="Req Pilot WFM Service", version="0.1.0")


@app.exception_handler(WfmServiceError)
async def wfm_service_error_handler(_: Request, exception: WfmServiceError) -> JSONResponse:
    return JSONResponse(
        status_code=exception.status_code,
        content={
            "error": {
                "code": exception.code,
                "message": exception.message,
                "details": exception.details,
            }
        },
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/internal/wfm/generate", response_model=GenerateWfmResponse)
def generate_wfm(request: GenerateWfmRequest) -> GenerateWfmResponse:
    return generator.generate(request)


@app.post("/internal/workflow/generate", response_model=GenerateWorkflowResponse)
def generate_workflow(request: GenerateWfmRequest) -> GenerateWorkflowResponse:
    return workflow_generator.generate(request)


@app.post("/internal/test-cases/generate", response_model=GenerateTestCasesResponse)
def generate_test_cases(request: GenerateTestCasesRequest) -> GenerateTestCasesResponse:
    return test_case_generator.generate(request)
