from app.schemas.wfm import WfmDocument
from app.services.flowchart_mapper import FlowchartMapper
from helpers import load_json_fixture


def test_flowchart_mapper_preserves_frontend_contract_from_wfm_fixture() -> None:
    wfm = WfmDocument(**load_json_fixture("valid_wfm_v1.json"))

    flowchart = FlowchartMapper().map(wfm)

    assert flowchart.nodes
    assert flowchart.edges
    assert flowchart.nodes[0].id == "N1"
    assert flowchart.nodes[0].label == "Start"
    assert flowchart.nodes[0].type == "START"

    manager_approval_edge = next(edge for edge in flowchart.edges if edge.id == "T3")
    assert manager_approval_edge.source == "N3"
    assert manager_approval_edge.target == "N4"
    assert manager_approval_edge.label == "Manager approves"
    assert manager_approval_edge.type == "YES"

    assert flowchart.mermaid.startswith("flowchart LR")
    assert "N3 --> |Manager approves| N4" in flowchart.mermaid
