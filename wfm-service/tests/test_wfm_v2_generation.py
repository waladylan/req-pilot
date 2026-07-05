import json

import pytest

from app.config import Settings
from app.schemas.request import GenerateWfmOptions, GenerateWfmRequest
from app.services.json_parser import JsonParser
from app.services.llm_client import LlmResponse
from app.services.prompt_builder_v2 import PromptBuilderV2
from app.services.rule_based_generator_v2 import RuleBasedGeneratorV2
from app.services.wfm_generator import WfmServiceError
from app.services.wfm_v2_canonicalizer import WfmV2Canonicalizer
from app.services.wfm_v2_generator import WfmV2Generator
from app.services.wfm_v2_validator import WfmV2Validator
from helpers import load_json_fixture


class FakeLlmClient:
    def __init__(self, content: str) -> None:
        self.content = content
        self.calls = 0

    def generate(self, system_prompt, user_prompt, options=None):  # type: ignore[no-untyped-def]
        self.calls += 1
        return LlmResponse(content=self.content, model="test-model")


def test_ai_mode_with_mocked_llm_returns_wfm_v2() -> None:
    llm_client = FakeLlmClient(json.dumps(load_json_fixture("wfm_v2_valid_purchase_request.json")))
    generator = generator_with(llm_client)

    wfm, metadata = generator.generate(request("AI"), "AI")

    assert wfm["wfmVersion"] == "2.0"
    assert metadata.generationMode == "AI"
    assert metadata.wfmSource == "python-wfm-v2-ai-generator"
    assert metadata.promptVersion == "wfm-v2-python-001"
    assert llm_client.calls == 1


def test_ai_mode_canonicalizes_ast_shaped_llm_response_to_wfm_v2() -> None:
    llm_client = FakeLlmClient(
        json.dumps(
            {
                "schemaVersion": "1.0",
                "modelType": "WORKFLOW_AST",
                "workflow": {"id": "login_flow", "title": "Login Flow"},
                "ast": {
                    "nodes": [
                        {"id": "N1", "role": "START", "title": "Start"},
                        {"id": "N2", "role": "ACTION", "title": "Enter credentials"},
                        {"id": "N3", "role": "DECISION", "title": "Credentials valid?"},
                        {"id": "N4", "role": "END", "title": "Logged in"},
                        {"id": "N5", "role": "END", "title": "Rejected"},
                    ],
                    "transitions": [
                        {"id": "T1", "from": "N1", "to": "N2"},
                        {"id": "T2", "from": "N2", "to": "N3"},
                        {"id": "T3", "from": "N3", "to": "N4", "semantic": "YES"},
                        {"id": "T4", "from": "N3", "to": "N5", "semantic": "NO"},
                    ],
                },
            }
        )
    )
    generator = generator_with(llm_client)

    wfm, metadata = generator.generate(request("AI"), "AI")

    assert wfm["wfmVersion"] == "2.0"
    assert wfm["workflowName"] == "Login Flow"
    assert wfm["nodes"][0]["kind"] == "START"
    assert wfm["transitions"][2]["label"] == "Yes"
    assert metadata.validationStatus == "PASSED"
    assert any("Canonicalized nodes from WFM AST shape" in warning for warning in metadata.warnings)


def test_rule_based_mode_returns_wfm_v2_without_llm() -> None:
    llm_client = FakeLlmClient("should not be called")
    generator = generator_with(llm_client)

    wfm, metadata = generator.generate(request("RULE_BASED"), "RULE_BASED")

    assert wfm["wfmVersion"] == "2.0"
    assert wfm["nodes"]
    assert metadata.wfmSource == "python-wfm-v2-rule-based-generator"
    assert llm_client.calls == 0


def test_mock_mode_returns_wfm_v2_without_llm() -> None:
    llm_client = FakeLlmClient("should not be called")
    generator = generator_with(llm_client)

    wfm, metadata = generator.generate(request("MOCK"), "MOCK")

    assert wfm["wfmVersion"] == "2.0"
    assert wfm["workflowId"] == "purchase_request_approval"
    assert metadata.generationMode == "MOCK"
    assert llm_client.calls == 0


def test_invalid_llm_output_returns_structured_error() -> None:
    generator = generator_with(FakeLlmClient("not json"))

    with pytest.raises(WfmServiceError) as exception:
        generator.generate(request("AI"), "AI")

    assert exception.value.code == "WFM_GENERATION_FAILED"


def request(mode: str) -> GenerateWfmRequest:
    return GenerateWfmRequest(
        requirement="User can create a purchase request. Manager approves. If amount > 5000, finance approval is required.",
        options=GenerateWfmOptions(generationMode=mode, wfmVersion="2.0"),
    )


def generator_with(llm_client: FakeLlmClient) -> WfmV2Generator:
    return WfmV2Generator(
        PromptBuilderV2(),
        llm_client,  # type: ignore[arg-type]
        JsonParser(),
        RuleBasedGeneratorV2(),
        WfmV2Canonicalizer(),
        WfmV2Validator(),
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
        prompt_version="wfm-v1-python-001",
        default_wfm_version="2.0",
        port=8001,
    )
