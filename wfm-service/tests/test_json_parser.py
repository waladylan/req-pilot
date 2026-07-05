import pytest

from app.services.json_parser import JsonParser
from app.services.json_parser import JsonParsingError


def test_parser_handles_raw_json() -> None:
    assert JsonParser().parse_first_object('{"schemaVersion":"1.0"}') == {"schemaVersion": "1.0"}


def test_parser_handles_markdown_fenced_json() -> None:
    payload = JsonParser().parse_first_object(
        """
```json
{"schemaVersion":"1.0","modelType":"WORKFLOW_AST"}
```
""".strip()
    )

    assert payload["modelType"] == "WORKFLOW_AST"


def test_parser_handles_generic_markdown_fence() -> None:
    payload = JsonParser().parse_first_object(
        """
```
{"schemaVersion":"1.0","modelType":"WORKFLOW_AST"}
```
""".strip()
    )

    assert payload["schemaVersion"] == "1.0"


def test_parser_extracts_first_json_object_from_text() -> None:
    payload = JsonParser().parse_first_object('Here is JSON: {"a":{"b":1}} trailing text')

    assert payload == {"a": {"b": 1}}


def test_parser_rejects_invalid_json_clearly() -> None:
    with pytest.raises(JsonParsingError, match="incomplete|invalid"):
        JsonParser().parse_first_object('{"schemaVersion":')


def test_parser_does_not_invent_fallback_wfm_when_json_is_missing() -> None:
    with pytest.raises(JsonParsingError, match="does not contain"):
        JsonParser().parse_first_object("I could not create the JSON.")
