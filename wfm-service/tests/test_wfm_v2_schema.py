import pytest
from pydantic import ValidationError

from app.schemas.wfm_v2 import WfmV2
from helpers import load_json_fixture


def test_valid_wfm_v2_loads_successfully() -> None:
    wfm = WfmV2(**load_json_fixture("wfm_v2_valid_purchase_request.json"))

    assert wfm.wfmVersion == "2.0"
    assert wfm.workflowId == "purchase_request_approval"
    assert wfm.nodes[0].id == "start"


def test_missing_wfm_version_fails() -> None:
    payload = load_json_fixture("wfm_v2_valid_purchase_request.json")
    payload.pop("wfmVersion")

    with pytest.raises(ValidationError):
        WfmV2(**payload)


def test_missing_node_id_fails() -> None:
    payload = load_json_fixture("wfm_v2_valid_purchase_request.json")
    payload["nodes"][0].pop("id")

    with pytest.raises(ValidationError):
        WfmV2(**payload)


def test_missing_transition_source_or_target_fails() -> None:
    payload = load_json_fixture("wfm_v2_valid_purchase_request.json")
    payload["transitions"][0].pop("source")

    with pytest.raises(ValidationError):
        WfmV2(**payload)
