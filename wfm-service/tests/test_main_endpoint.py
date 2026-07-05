import json

from fastapi.testclient import TestClient

from app.config import Settings
import app.main as main
from app.schemas.request import GenerateWfmRequest
from app.services.json_parser import JsonParser
from app.services.llm_client import LlmClientError, LlmResponse
from app.services.prompt_builder import PromptBuilder
from app.services.prompt_builder_v2 import PromptBuilderV2
from app.services.rule_based_generator_v2 import RuleBasedGeneratorV2
from app.services.wfm_generator import WfmGenerator, WfmServiceError
from app.services.wfm_validator import WfmValidator
from app.services.wfm_v2_canonicalizer import WfmV2Canonicalizer
from app.services.wfm_v2_generator import WfmV2Generator
from app.services.wfm_v2_validator import WfmV2Validator
from helpers import load_json_fixture


class FakeLlmClient:
    def __init__(self, content: str | None = None, exception: Exception | None = None) -> None:
        self.content = content
        self.exception = exception
        self.calls = 0

    def generate(self, system_prompt, user_prompt, options=None):  # type: ignore[no-untyped-def]
        self.calls += 1
        if self.exception is not None:
            raise self.exception
        return LlmResponse(content=self.content, model="test-model")


def test_health_endpoint_returns_up_without_llm_call(monkeypatch) -> None:
    llm_client = FakeLlmClient(exception=AssertionError("health must not call the LLM"))
    monkeypatch.setattr(main, "workflow_generator", workflow_generator_with(llm_client))
    client = TestClient(main.app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}
    assert llm_client.calls == 0


def test_generate_endpoint_returns_wfm_only_without_flowchart_or_mermaid(monkeypatch) -> None:
    llm_client = FakeLlmClient(json.dumps(load_json_fixture("valid_wfm_v1.json")))
    monkeypatch.setattr(main, "generator", generator_with(llm_client))
    client = TestClient(main.app)

    response = client.post(
        "/internal/wfm/generate",
        json={
            "requirement": "Employee submits a leave request. Manager approves or rejects it.",
            "context": {"projectId": "test-project", "language": "en"},
            "options": {"temperature": 0},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert "wfm" in body
    assert "metadata" in body
    assert body["metadata"]["validationStatus"] == "PASSED"
    assert body["wfm"]["schemaVersion"] == "1.0"
    assert body["wfm"]["modelType"] == "WORKFLOW_AST"
    assert "flowchart" not in body
    assert "mermaid" not in json.dumps(body).lower()
    assert "reactFlowType" not in json.dumps(body)
    assert llm_client.calls == 1


def test_workflow_endpoint_returns_wfm_flowchart_and_metadata_in_mock_mode(monkeypatch) -> None:
    llm_client = FakeLlmClient(exception=AssertionError("MOCK mode must not call the LLM"))
    monkeypatch.setattr(main, "workflow_generator", workflow_generator_with(llm_client))
    client = TestClient(main.app)

    response = client.post(
        "/internal/workflow/generate",
        json={
            "requirement": "Feature: Purchase request\nUser creates a purchase request. Manager approves.",
            "options": {"generationMode": "MOCK"},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert "wfm" in body
    assert "flowchart" in body
    assert "metadata" in body
    assert body["flowchart"]["nodes"]
    assert body["flowchart"]["edges"]
    assert body["metadata"]["generationMode"] == "MOCK"
    assert body["metadata"]["wfmSource"] == "python-mock-generator"
    assert body["metadata"]["flowchartSource"] == "python-flowchart-mapper"
    assert body["metadata"]["mappingStatus"] == "PASSED"
    assert llm_client.calls == 0


def test_workflow_endpoint_supports_rule_based_mode_without_llm(monkeypatch) -> None:
    llm_client = FakeLlmClient(exception=AssertionError("RULE_BASED mode must not call the LLM"))
    monkeypatch.setattr(main, "workflow_generator", workflow_generator_with(llm_client))
    client = TestClient(main.app)

    response = client.post(
        "/internal/workflow/generate",
        json={
            "requirement": "Feature: Delete product\nStart\nUser deletes product. End.",
            "options": {"generationMode": "RULE_BASED"},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["metadata"]["generationMode"] == "RULE_BASED"
    assert body["metadata"]["wfmSource"] == "python-rule-based-generator"
    assert body["metadata"]["flowchartSource"] == "python-flowchart-mapper"
    assert body["flowchart"]["nodes"][0]["type"] == "START"
    assert [node["label"] for node in body["flowchart"]["nodes"]].count("End") == 1
    assert llm_client.calls == 0


def test_workflow_endpoint_supports_ai_mode_with_mocked_llm(monkeypatch) -> None:
    llm_client = FakeLlmClient(json.dumps(load_json_fixture("valid_wfm_v1.json")))
    monkeypatch.setattr(main, "workflow_generator", workflow_generator_with(llm_client))
    client = TestClient(main.app)

    response = client.post(
        "/internal/workflow/generate",
        json={
            "requirement": "Employee submits a leave request. Manager approves or rejects it.",
            "options": {"generationMode": "AI"},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["wfm"]["schemaVersion"] == "1.0"
    assert body["flowchart"]["edges"][2]["label"] == "Manager approves"
    assert body["metadata"]["generationMode"] == "AI"
    assert body["metadata"]["wfmSource"] == "python-ai-generator"
    assert body["metadata"]["flowchartSource"] == "python-flowchart-mapper"
    assert llm_client.calls == 1


def test_workflow_endpoint_supports_wfm_v2_mock_mode(monkeypatch) -> None:
    llm_client = FakeLlmClient(exception=AssertionError("MOCK mode must not call the LLM"))
    monkeypatch.setattr(main, "workflow_generator", workflow_generator_with(llm_client))
    client = TestClient(main.app)

    response = client.post(
        "/internal/workflow/generate",
        json={
            "requirement": "User can create a purchase request. Manager approves. If amount > 5000, finance approval is required.",
            "options": {"generationMode": "MOCK", "wfmVersion": "2.0"},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["wfm"]["wfmVersion"] == "2.0"
    assert body["flowchart"]["nodes"]
    assert body["flowchart"]["edges"]
    assert body["metadata"]["wfmVersion"] == "2.0"
    assert body["metadata"]["generationMode"] == "MOCK"
    assert body["metadata"]["validationStatus"] == "PASSED"
    assert body["metadata"]["canonicalizationStatus"] == "PASSED"
    assert body["metadata"]["mappingStatus"] == "PASSED"
    assert body["metadata"]["flowchartSource"] == "python-wfm-v2-flowchart-mapper"
    assert llm_client.calls == 0


def test_generate_endpoint_rejects_invalid_requests() -> None:
    client = TestClient(main.app)

    missing_requirement = client.post("/internal/wfm/generate", json={"options": {"temperature": 0}})
    empty_requirement = client.post("/internal/wfm/generate", json={"requirement": ""})
    invalid_body = client.post("/internal/wfm/generate", content="not-json")

    assert missing_requirement.status_code == 422
    assert empty_requirement.status_code == 422
    assert invalid_body.status_code == 422
    assert "traceback" not in missing_requirement.text.lower()
    assert "traceback" not in invalid_body.text.lower()


def test_generate_endpoint_returns_structured_error_for_invalid_llm_json(monkeypatch) -> None:
    monkeypatch.setattr(main, "generator", generator_with(FakeLlmClient("not json")))
    client = TestClient(main.app)

    response = client.post("/internal/wfm/generate", json={"requirement": "Feature: Login"})

    assert response.status_code == 502
    assert response.json()["error"]["code"] == "WFM_GENERATION_FAILED"
    assert "details" in response.json()["error"]


def test_generate_endpoint_returns_structured_error_for_validation_failure(monkeypatch) -> None:
    invalid_wfm = load_json_fixture("valid_wfm_v1.json")
    invalid_wfm["schemaVersion"] = "2.0"
    monkeypatch.setattr(main, "generator", generator_with(FakeLlmClient(json.dumps(invalid_wfm))))
    client = TestClient(main.app)

    response = client.post("/internal/wfm/generate", json={"requirement": "Feature: Login"})

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "WFM_VALIDATION_FAILED"


def test_generate_endpoint_returns_structured_error_for_llm_exception(monkeypatch) -> None:
    monkeypatch.setattr(main, "generator", generator_with(FakeLlmClient(exception=LlmClientError("provider down", 503))))
    client = TestClient(main.app)

    response = client.post("/internal/wfm/generate", json={"requirement": "Feature: Login"})

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "WFM_GENERATION_FAILED"


def generator_with(llm_client: FakeLlmClient) -> WfmGenerator:
    return WfmGenerator(
        PromptBuilder("test-prompt"),
        llm_client,  # type: ignore[arg-type]
        JsonParser(),
        WfmValidator(),
        settings(),
    )


def workflow_generator_with(llm_client: FakeLlmClient):  # type: ignore[no-untyped-def]
    from app.services.flowchart_mapper import FlowchartMapper
    from app.services.rule_based_generator import RuleBasedGenerator
    from app.services.wfm_normalizer import WfmNormalizer
    from app.services.workflow_generator import WorkflowGenerator

    return WorkflowGenerator(
        generator_with(llm_client),
        RuleBasedGenerator(),
        WfmV2Generator(
            PromptBuilderV2(),
            llm_client,  # type: ignore[arg-type]
            JsonParser(),
            RuleBasedGeneratorV2(),
            WfmV2Canonicalizer(),
            WfmV2Validator(),
            settings(),
        ),
        WfmNormalizer(),
        WfmValidator(),
        FlowchartMapper(),
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
