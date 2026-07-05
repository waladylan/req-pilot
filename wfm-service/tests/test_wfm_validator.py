import pytest

from app.services.wfm_validator import WfmValidationError, WfmValidator
from helpers import load_json_fixture


def test_validator_accepts_valid_current_wfm_v1() -> None:
    assert WfmValidator().validate(load_json_fixture("valid_wfm_v1.json")) == []


def test_validator_rejects_missing_required_fields() -> None:
    with pytest.raises(WfmValidationError) as error:
        WfmValidator().validate({"schemaVersion": "1.0"})

    assert "$.modelType must be WORKFLOW_AST" in error.value.errors
    assert "$.workflow is required" in error.value.errors


def test_validator_rejects_ui_fields() -> None:
    payload = load_json_fixture("valid_wfm_v1.json")
    payload["ast"]["nodes"][0]["position"] = {"x": 1, "y": 2}

    with pytest.raises(WfmValidationError) as error:
        WfmValidator().validate(payload)

    assert "$.ast.nodes[0].position is a UI-only field and is not allowed in WFM" in error.value.errors


def test_validator_rejects_missing_node_id() -> None:
    payload = load_json_fixture("valid_wfm_v1.json")
    del payload["ast"]["nodes"][1]["id"]

    with pytest.raises(WfmValidationError) as error:
        WfmValidator().validate(payload)

    assert "$.ast.nodes[1].id is required" in error.value.errors


def test_validator_rejects_missing_node_role() -> None:
    payload = load_json_fixture("valid_wfm_v1.json")
    del payload["ast"]["nodes"][1]["role"]

    with pytest.raises(WfmValidationError) as error:
        WfmValidator().validate(payload)

    assert "$.ast.nodes[1].role is invalid" in error.value.errors


def test_validator_rejects_transition_reference_to_missing_node() -> None:
    payload = load_json_fixture("valid_wfm_v1.json")
    payload["ast"]["transitions"][0]["to"] = "MISSING"

    with pytest.raises(WfmValidationError) as error:
        WfmValidator().validate(payload)

    assert "$.ast.transitions[0].to must reference an existing node" in error.value.errors


def test_validator_does_not_enforce_wfm_v2_action_branching_rules() -> None:
    payload = load_json_fixture("valid_wfm_v1.json")
    payload["ast"]["nodes"][1]["role"] = "ACTION"
    payload["ast"]["transitions"].append(
        {"id": "T11", "from": "N2", "to": "N7", "semantic": "FAILURE", "condition": "Submission failed"}
    )

    assert WfmValidator().validate(payload) == []
