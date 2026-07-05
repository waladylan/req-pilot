import json
from typing import Any


class JsonParsingError(ValueError):
    pass


class JsonParser:
    def parse_first_object(self, text: str) -> dict[str, Any]:
        if not text or not text.strip():
            raise JsonParsingError("LLM response is empty")

        json_text = self.extract_first_object(self.strip_code_fence(text.strip()))
        try:
            parsed = json.loads(json_text)
        except json.JSONDecodeError as exception:
            raise JsonParsingError(f"LLM response JSON is invalid: {exception.msg}") from exception

        if not isinstance(parsed, dict):
            raise JsonParsingError("LLM response JSON must be an object")
        return parsed

    def strip_code_fence(self, text: str) -> str:
        if not text.startswith("```"):
            return text
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        return "\n".join(lines).strip()

    def extract_first_object(self, text: str) -> str:
        start = text.find("{")
        if start < 0:
            raise JsonParsingError("LLM response does not contain a JSON object")

        depth = 0
        in_string = False
        escaped = False

        for index in range(start, len(text)):
            char = text[index]
            if in_string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
                continue

            if char == '"':
                in_string = True
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return text[start : index + 1]

        raise JsonParsingError("LLM response JSON object is incomplete")
