from app.services.flowchart_mapper import FlowchartMapper
from helpers import load_json_fixture


def test_wfm_v2_maps_to_flowchart_and_preserves_edge_labels() -> None:
    flowchart = FlowchartMapper().map(load_json_fixture("wfm_v2_valid_purchase_request.json"))

    assert flowchart.nodes
    assert flowchart.edges
    assert flowchart.nodes[0].id == "start"
    assert flowchart.nodes[0].type == "START"
    assert next(node for node in flowchart.nodes if node.id == "manager_approval").type == "ACTION"
    assert next(node for node in flowchart.nodes if node.id == "manager_approved").type == "DECISION"

    approved_edge = next(edge for edge in flowchart.edges if edge.id == "t_manager_approved")
    assert approved_edge.label == "Approved"
    assert approved_edge.type == "YES"

    amount_edge = next(edge for edge in flowchart.edges if edge.id == "t_amount_yes")
    assert amount_edge.label == "Yes"
    assert amount_edge.type == "YES"
    assert flowchart.mermaid.startswith("flowchart LR")
