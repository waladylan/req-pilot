from copy import deepcopy

from fastapi.testclient import TestClient

from app.schemas.request import GenerateTestCasesRequest
from app.services.test_case_generator import WfmV2TestCaseGenerator
from app.services.wfm_generator import WfmServiceError
from app.services.wfm_v2_validator import WfmV2Validator
import app.main as main
from helpers import load_json_fixture


def test_purchase_request_wfm_v2_generates_expected_path_cases() -> None:
    response = generator().generate(GenerateTestCasesRequest(wfm=purchase_request_wfm()))
    test_cases = response.testCaseSet.testCases

    assert [test_case.id for test_case in test_cases] == ["TC-001", "TC-002", "TC-003", "TC-004"]
    titles = [test_case.title for test_case in test_cases]
    assert titles[0] == "Purchase Request Approval - Manager rejects"
    assert titles[1] == "Purchase Request Approval - Manager approves and Amount <= 1000"
    assert titles[2] == "Purchase Request Approval - Manager approves and Amount > 1000 and Finance approves"
    assert titles[3] == "Purchase Request Approval - Manager approves and Amount > 1000 and Finance rejects"
    assert all(test_case.steps for test_case in test_cases)
    assert all(test_case.expectedResult for test_case in test_cases)
    assert "t_finance_rejected" in response.testCaseSet.coverage.coveredTransitionIds
    assert response.testCaseSet.coverage.uncoveredTransitionIds == []
    assert response.metadata.generator == "python-wfm-v2-test-case-generator"


def test_simple_linear_workflow_generates_single_success_case() -> None:
    wfm = {
        "wfmVersion": "2.0",
        "workflowId": "login",
        "workflowName": "Login",
        "nodes": [
            {"id": "start", "kind": "START", "name": "Start"},
            {"id": "enter_credentials", "kind": "USER_TASK", "name": "Enter credentials", "actor": "User"},
            {"id": "end", "kind": "END", "name": "Logged in"},
        ],
        "transitions": [
            {"id": "t1", "source": "start", "target": "enter_credentials"},
            {"id": "t2", "source": "enter_credentials", "target": "end"},
        ],
    }

    response = generator().generate(GenerateTestCasesRequest(wfm=wfm))

    assert len(response.testCaseSet.testCases) == 1
    assert response.testCaseSet.testCases[0].title == "Login - Logged in"
    assert response.testCaseSet.testCases[0].tags == ["success", "user-task"]


def test_cycle_returns_warning_and_does_not_loop_forever() -> None:
    wfm = deepcopy(purchase_request_wfm())
    wfm["transitions"].append(
        {
            "id": "t_retry",
            "source": "request_rejected",
            "target": "create_purchase_request",
            "label": "Retry",
            "data": {"loop": True},
        }
    )
    for node in wfm["nodes"]:
        if node["id"] == "request_rejected":
            node["kind"] = "ACTION"

    response = generator().generate(GenerateTestCasesRequest(wfm=wfm))

    assert response.testCaseSet.testCases
    assert any("Cycle skipped" in warning for warning in response.metadata.warnings)


def test_missing_start_returns_structured_error() -> None:
    wfm = deepcopy(purchase_request_wfm())
    wfm["nodes"] = [node for node in wfm["nodes"] if node["kind"] != "START"]

    try:
        generator().generate(GenerateTestCasesRequest(wfm=wfm))
    except WfmServiceError as exception:
        assert exception.code == "TEST_CASE_GENERATION_FAILED"
        assert exception.status_code == 422
    else:
        raise AssertionError("Expected WfmServiceError")


def test_endpoint_returns_test_case_set_for_valid_wfm_v2() -> None:
    client = TestClient(main.app)

    response = client.post(
        "/internal/test-cases/generate",
        json={
            "wfm": purchase_request_wfm(),
            "context": {"projectId": "project-1", "requirementId": "requirement-1"},
            "options": {"strategy": "PATH_COVERAGE", "maxCases": 30, "includeNegativeCases": True},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["testCaseSet"]["sourceWfmVersion"] == "2.0"
    assert body["testCaseSet"]["testCases"][0]["id"] == "TC-001"
    assert body["metadata"]["generationStatus"] == "PASSED"


def test_endpoint_rejects_wfm_v1() -> None:
    client = TestClient(main.app)

    response = client.post(
        "/internal/test-cases/generate",
        json={
            "wfm": load_json_fixture("valid_wfm_v1.json"),
        },
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "TEST_CASE_GENERATION_FAILED"


def purchase_request_wfm() -> dict:
    return deepcopy(load_json_fixture("wfm_v2_valid_purchase_request.json"))


def generator() -> WfmV2TestCaseGenerator:
    return WfmV2TestCaseGenerator(WfmV2Validator())
