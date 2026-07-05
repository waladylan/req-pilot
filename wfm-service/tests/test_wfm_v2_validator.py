from copy import deepcopy

from app.services.wfm_v2_validator import WfmV2Validator
from helpers import load_json_fixture


def codes(result):  # type: ignore[no-untyped-def]
    return {issue.code for issue in result.errors}


def test_valid_simple_workflow_passes() -> None:
    result = WfmV2Validator().validate(load_json_fixture("wfm_v2_valid_leave_request.json"))

    assert result.valid is True
    assert result.errors == []


def test_missing_start_fails() -> None:
    payload = load_json_fixture("wfm_v2_valid_leave_request.json")
    payload["nodes"] = [node for node in payload["nodes"] if node["kind"] != "START"]

    result = WfmV2Validator().validate(payload)

    assert result.valid is False
    assert "START_COUNT_INVALID" in codes(result)


def test_multiple_start_nodes_fail() -> None:
    payload = load_json_fixture("wfm_v2_valid_leave_request.json")
    payload["nodes"].append({"id": "start_2", "kind": "START", "name": "Start Again", "data": {}})

    result = WfmV2Validator().validate(payload)

    assert "START_COUNT_INVALID" in codes(result)


def test_end_with_outgoing_transition_fails() -> None:
    payload = load_json_fixture("wfm_v2_valid_leave_request.json")
    payload["transitions"].append({"id": "t_bad", "source": "approved", "target": "rejected", "data": {}})

    result = WfmV2Validator().validate(payload)

    assert "END_HAS_OUTGOING" in codes(result)


def test_transition_target_missing_fails() -> None:
    result = WfmV2Validator().validate(load_json_fixture("wfm_v2_invalid_missing_transition_target.json"))

    assert "TRANSITION_TARGET_NOT_FOUND" in codes(result)


def test_decision_with_one_outgoing_transition_fails() -> None:
    payload = load_json_fixture("wfm_v2_valid_leave_request.json")
    payload["transitions"] = [transition for transition in payload["transitions"] if transition["id"] != "t_rejected"]

    result = WfmV2Validator().validate(payload)

    assert "DECISION_OUTGOING_INVALID" in codes(result)


def test_decision_transition_without_label_or_condition_fails() -> None:
    payload = load_json_fixture("wfm_v2_valid_leave_request.json")
    for transition in payload["transitions"]:
        if transition["source"] == "manager_approved":
            transition["label"] = None
            transition["condition"] = None
            transition["outcome"] = None

    result = WfmV2Validator().validate(payload)

    assert "DECISION_TRANSITION_MEANING_REQUIRED" in codes(result)


def test_action_with_multiple_outgoing_transitions_fails() -> None:
    payload = load_json_fixture("wfm_v2_valid_leave_request.json")
    payload["nodes"].append({"id": "extra_end", "kind": "END", "name": "Extra End", "data": {}})
    payload["transitions"].append(
        {"id": "t_extra", "source": "submit_leave", "target": "extra_end", "label": "Other", "data": {}}
    )

    result = WfmV2Validator().validate(payload)

    assert "LINEAR_TASK_OUTGOING_INVALID" in codes(result)


def test_approval_branching_by_business_condition_fails() -> None:
    result = WfmV2Validator().validate(load_json_fixture("wfm_v2_invalid_approval_mixed_condition.json"))

    assert "APPROVAL_MIXES_BUSINESS_CONDITION" in codes(result)


def test_approval_with_approved_rejected_outcomes_passes() -> None:
    payload = load_json_fixture("wfm_v2_valid_leave_request.json")

    result = WfmV2Validator().validate(payload)

    assert result.valid is True


def test_orphan_node_produces_error() -> None:
    payload = deepcopy(load_json_fixture("wfm_v2_valid_leave_request.json"))
    payload["nodes"].append({"id": "orphan", "kind": "ACTION", "name": "Orphan", "data": {}})

    result = WfmV2Validator().validate(payload)

    assert "NODE_UNREACHABLE" in codes(result)
