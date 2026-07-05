import json

import pytest

from app.config import Settings
from app.schemas.request import GenerateWfmRequest
from app.services.json_parser import JsonParser
from app.services.llm_client import LlmClientError
from app.services.llm_client import LlmResponse
from app.services.prompt_builder import PromptBuilder
from app.services.wfm_generator import WfmGenerator, WfmServiceError
from app.services.wfm_validator import WfmValidator
from helpers import load_json_fixture


class FakeLlmClient:
    def __init__(self, content: str | None = None, exception: Exception | None = None) -> None:
        self.content = content
        self.exception = exception
        self.calls = 0
        self.system_prompt = ""
        self.user_prompt = ""

    def generate(self, system_prompt, user_prompt, options=None):  # type: ignore[no-untyped-def]
        self.calls += 1
        self.system_prompt = system_prompt
        self.user_prompt = user_prompt
        if self.exception is not None:
            raise self.exception
        return LlmResponse(content=self.content, model="test-model")


def test_generator_returns_wfm_response_contract() -> None:
    llm_client = FakeLlmClient(json.dumps(load_json_fixture("valid_wfm_v1.json")))
    generator = WfmGenerator(
        PromptBuilder("test-prompt"),
        llm_client,  # type: ignore[arg-type]
        JsonParser(),
        WfmValidator(),
        settings(),
    )

    response = generator.generate(GenerateWfmRequest(requirement="Feature: Login"))

    assert llm_client.calls == 1
    assert "WORKFLOW_AST" in llm_client.system_prompt
    assert response.wfm.schemaVersion == "1.0"
    assert response.wfm.modelType == "WORKFLOW_AST"
    assert response.wfm.workflow.id == "leave-request-approval"
    assert response.metadata.model == "test-model"
    assert response.metadata.promptVersion == "test-prompt"
    assert response.metadata.validationStatus == "PASSED"
    assert "Feature: Login" in llm_client.user_prompt


def test_generator_wraps_invalid_llm_json_as_structured_error() -> None:
    generator = generator_with(FakeLlmClient("not json"))

    with pytest.raises(WfmServiceError) as error:
        generator.generate(GenerateWfmRequest(requirement="Feature: Login"))

    assert error.value.code == "WFM_GENERATION_FAILED"
    assert error.value.status_code == 502
    assert "reason" in error.value.details


def test_generator_wraps_wfm_validation_failure_as_structured_error() -> None:
    invalid_wfm = load_json_fixture("valid_wfm_v1.json")
    invalid_wfm["ast"]["transitions"][0]["to"] = "MISSING"
    generator = generator_with(FakeLlmClient(json.dumps(invalid_wfm)))

    with pytest.raises(WfmServiceError) as error:
        generator.generate(GenerateWfmRequest(requirement="Feature: Login"))

    assert error.value.code == "WFM_VALIDATION_FAILED"
    assert error.value.status_code == 422
    assert "$.ast.transitions[0].to must reference an existing node" in error.value.details["errors"]


def test_generator_wraps_llm_client_failure_as_structured_error() -> None:
    generator = generator_with(FakeLlmClient(exception=LlmClientError("provider down", 503)))

    with pytest.raises(WfmServiceError) as error:
        generator.generate(GenerateWfmRequest(requirement="Feature: Login"))

    assert error.value.code == "WFM_GENERATION_FAILED"
    assert error.value.status_code == 503
    assert error.value.details["reason"] == "provider down"


def generator_with(llm_client: FakeLlmClient) -> WfmGenerator:
    return WfmGenerator(
        PromptBuilder("test-prompt"),
        llm_client,  # type: ignore[arg-type]
        JsonParser(),
        WfmValidator(),
        settings(),
    )


def settings() -> Settings:
    return Settings(
        llm_provider="openrouter",
        openai_api_key="",
        openai_model="gpt-4o-mini",
        openai_base_url="https://api.openai.com/v1",
        openrouter_api_key="test-key",
        openrouter_model="deepseek/deepseek-chat",
        openrouter_base_url="https://openrouter.ai/api/v1",
        request_timeout_seconds=60,
        max_output_tokens=4096,
        temperature=0.2,
        prompt_version="test-prompt",
        default_wfm_version="1.0",
        port=8001,
    )
