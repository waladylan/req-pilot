from app.services.wfm_v2_canonicalizer import WfmV2Canonicalizer
from app.services.wfm_v2_validator import WfmV2Validator


def test_canonicalizer_normalizes_node_kind_casing() -> None:
    payload = {
        "wfmVersion": "2.0",
        "workflowName": "Demo",
        "nodes": [
            {"id": "Start", "kind": "start", "name": "Start"},
            {"id": "End", "kind": "end", "name": "End"},
        ],
        "transitions": [{"source": "Start", "target": "End"}],
    }

    canonical, warnings = WfmV2Canonicalizer().canonicalize(payload)

    assert canonical["nodes"][0]["kind"] == "START"
    assert canonical["nodes"][1]["kind"] == "END"
    assert canonical["transitions"][0]["source"] == "start"
    assert canonical["transitions"][0]["target"] == "end"
    assert warnings


def test_canonicalizer_adds_transition_ids_when_missing() -> None:
    payload = {
        "wfmVersion": "2.0",
        "workflowName": "Demo",
        "nodes": [
            {"id": "start", "kind": "START", "name": "Start"},
            {"id": "end", "kind": "END", "name": "End"},
        ],
        "transitions": [{"source": "start", "target": "end"}],
    }

    canonical, warnings = WfmV2Canonicalizer().canonicalize(payload)

    assert canonical["transitions"][0]["id"] == "t_1"
    assert any("Generated missing id" in warning for warning in warnings)


def test_canonicalizer_preserves_business_labels() -> None:
    payload = {
        "wfmVersion": "2.0",
        "workflowName": "Demo",
        "nodes": [
            {"id": "start", "kind": "START", "name": "Start"},
            {"id": "decision", "kind": "DECISION", "name": "Amount > 1000?"},
            {"id": "end", "kind": "END", "name": "End"},
        ],
        "transitions": [
            {"id": "t1", "source": "start", "target": "decision"},
            {"id": "t2", "source": "decision", "target": "end", "label": "Amount > 1000"},
        ],
    }

    canonical, _ = WfmV2Canonicalizer().canonicalize(payload)

    assert canonical["transitions"][1]["label"] == "Amount > 1000"
    assert canonical["transitions"][1]["condition"] is None


def test_canonicalizer_does_not_invent_missing_business_logic() -> None:
    payload = {
        "wfmVersion": "2.0",
        "workflowName": "Demo",
        "nodes": [
            {"id": "start", "kind": "START", "name": "Start"},
            {"id": "end", "kind": "END", "name": "End"},
        ],
        "transitions": [{"id": "t1", "source": "start", "target": "end"}],
    }

    canonical, _ = WfmV2Canonicalizer().canonicalize(payload)

    assert canonical["transitions"][0]["label"] is None
    assert canonical["transitions"][0]["condition"] is None
    assert canonical["transitions"][0]["outcome"] is None


def test_canonicalizer_accepts_ast_role_title_and_from_to_shape() -> None:
    payload = {
        "schemaVersion": "1.0",
        "modelType": "WORKFLOW_AST",
        "workflow": {"id": "login_flow", "title": "Login Flow"},
        "ast": {
            "nodes": [
                {"id": "N1", "role": "START", "title": "Start", "position": {"x": 0, "y": 0}},
                {"id": "N2", "role": "ACTION", "title": "Enter credentials"},
                {"id": "N3", "role": "DECISION", "title": "Credentials valid?"},
                {"id": "N4", "role": "END", "title": "Logged in"},
                {"id": "N5", "role": "END", "title": "Rejected"},
            ],
            "transitions": [
                {"id": "T1", "from": "N1", "to": "N2", "sourceHandle": "OUTPUT"},
                {"id": "T2", "from": "N2", "to": "N3"},
                {"id": "T3", "from": "N3", "to": "N4", "semantic": "YES"},
                {"id": "T4", "from": "N3", "to": "N5", "semantic": "NO"},
            ],
        },
    }

    canonical, warnings = WfmV2Canonicalizer().canonicalize(payload)

    assert canonical["wfmVersion"] == "2.0"
    assert canonical["workflowId"] == "login_flow"
    assert canonical["workflowName"] == "Login Flow"
    assert canonical["nodes"][0]["id"] == "n1"
    assert canonical["nodes"][0]["kind"] == "START"
    assert canonical["nodes"][1]["name"] == "Enter credentials"
    assert canonical["transitions"][0]["source"] == "n1"
    assert canonical["transitions"][0]["target"] == "n2"
    assert canonical["transitions"][2]["label"] == "Yes"
    assert canonical["transitions"][2]["data"]["semantic"] == "YES"
    assert "position" not in canonical["nodes"][0]
    assert "sourceHandle" not in canonical["transitions"][0]
    assert any("Canonicalized nodes from WFM AST shape" in warning for warning in warnings)


def test_canonicalizer_marks_retry_cycle_as_loop_edge() -> None:
    payload = {
        "wfmVersion": "2.0",
        "workflowName": "Login Retry",
        "nodes": [
            {"id": "start", "kind": "START", "name": "Start"},
            {"id": "login", "kind": "USER_TASK", "name": "Enter Credentials"},
            {"id": "valid", "kind": "DECISION", "name": "Credentials Valid?"},
            {"id": "success", "kind": "END", "name": "Logged In"},
            {"id": "retry", "kind": "DECISION", "name": "Retry?"},
            {"id": "cancelled", "kind": "END", "name": "Login Cancelled"},
        ],
        "transitions": [
            {"source": "start", "target": "login"},
            {"source": "login", "target": "valid"},
            {"source": "valid", "target": "success", "semantic": "YES"},
            {"source": "valid", "target": "retry", "semantic": "NO"},
            {"source": "retry", "target": "login", "semantic": "RETRY"},
            {"source": "retry", "target": "cancelled", "semantic": "CANCEL"},
        ],
    }

    canonical, warnings = WfmV2Canonicalizer().canonicalize(payload)
    retry_transition = next(transition for transition in canonical["transitions"] if transition["source"] == "retry")

    assert retry_transition["target"] == "login"
    assert retry_transition["data"]["loop"] is True
    assert any("Marked transition" in warning for warning in warnings)
    assert WfmV2Validator().validate(canonical).valid is True


def test_canonicalizer_converts_branching_linear_task_to_decision() -> None:
    payload = {
        "wfmVersion": "2.0",
        "workflowName": "Login",
        "nodes": [
            {"id": "start", "kind": "START", "name": "Start"},
            {"id": "validate", "kind": "SYSTEM_TASK", "name": "Validate credentials"},
            {"id": "success", "kind": "END", "name": "Logged in"},
            {"id": "failure", "kind": "END", "name": "Show invalid credentials"},
        ],
        "transitions": [
            {"source": "start", "target": "validate"},
            {"source": "validate", "target": "success", "semantic": "SUCCESS"},
            {"source": "validate", "target": "failure", "semantic": "FAILURE"},
        ],
    }

    canonical, warnings = WfmV2Canonicalizer().canonicalize(payload)

    validate_node = next(node for node in canonical["nodes"] if node["id"] == "validate")
    assert validate_node["kind"] == "DECISION"
    assert any("Normalized branching SYSTEM_TASK" in warning for warning in warnings)
    assert WfmV2Validator().validate(canonical).valid is True


def test_canonicalizer_fills_missing_decision_transition_meaning_from_target() -> None:
    payload = {
        "wfmVersion": "2.0",
        "workflowName": "Login",
        "nodes": [
            {"id": "start", "kind": "START", "name": "Start"},
            {"id": "valid", "kind": "DECISION", "name": "Credentials valid?"},
            {"id": "success", "kind": "END", "name": "Login Success"},
            {"id": "invalid", "kind": "ERROR", "name": "Invalid Credentials Error"},
        ],
        "transitions": [
            {"source": "start", "target": "valid"},
            {"source": "valid", "target": "success"},
            {"source": "valid", "target": "invalid"},
        ],
    }

    canonical, warnings = WfmV2Canonicalizer().canonicalize(payload)

    success_transition = next(transition for transition in canonical["transitions"] if transition["target"] == "success")
    failure_transition = next(transition for transition in canonical["transitions"] if transition["target"] == "invalid")
    assert success_transition["label"] == "Success"
    assert success_transition["data"]["semantic"] == "SUCCESS"
    assert failure_transition["label"] == "Failure"
    assert failure_transition["data"]["semantic"] == "FAILURE"
    assert any("Filled missing branch meaning" in warning for warning in warnings)
    assert WfmV2Validator().validate(canonical).valid is True
