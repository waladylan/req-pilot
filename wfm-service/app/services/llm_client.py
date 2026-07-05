import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

from app.config import Settings
from app.schemas.request import GenerateWfmOptions


class LlmClientError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


@dataclass(frozen=True)
class LlmResponse:
    content: str
    model: str


class LlmClient:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    def generate(self, system_prompt: str, user_prompt: str, options: GenerateWfmOptions | None = None) -> LlmResponse:
        if self.settings.llm_provider == "mock":
            return self._mock_response(user_prompt, options)

        api_key = self.settings.api_key_for_provider()
        if not api_key:
            raise LlmClientError(f"{self.settings.llm_provider} API key is not configured", 401)

        model = self.settings.model_for_provider(options.model if options else None)
        temperature = options.temperature if options and options.temperature is not None else self.settings.temperature
        body = self._request_body(model, system_prompt, user_prompt, temperature)
        request = urllib.request.Request(
            self._chat_completions_url(),
            data=json.dumps(body).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )

        try:
            with urllib.request.urlopen(request, timeout=self.settings.request_timeout_seconds) as response:
                response_body = response.read().decode("utf-8")
        except urllib.error.HTTPError as exception:
            response_body = exception.read().decode("utf-8", errors="replace")
            raise LlmClientError(self._error_message(response_body) or "LLM provider returned an error", exception.code) from exception
        except TimeoutError as exception:
            raise LlmClientError("LLM provider timed out", 408) from exception
        except OSError as exception:
            raise LlmClientError("LLM provider is unavailable") from exception

        return self._parse_response(response_body, model)

    def _chat_completions_url(self) -> str:
        base_url = self.settings.base_url_for_provider().rstrip("/")
        if not base_url:
            raise LlmClientError(f"Unsupported LLM provider: {self.settings.llm_provider}", 400)
        return f"{base_url}/chat/completions"

    def _request_body(self, model: str, system_prompt: str, user_prompt: str, temperature: float) -> dict[str, Any]:
        return {
            "model": model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": temperature,
            "max_tokens": self.settings.max_output_tokens,
            "stream": False,
        }

    def _parse_response(self, response_body: str, fallback_model: str) -> LlmResponse:
        try:
            payload = json.loads(response_body)
            content = payload["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exception:
            raise LlmClientError("LLM provider response is invalid", 502) from exception

        if not isinstance(content, str) or not content.strip():
            raise LlmClientError("LLM provider returned empty content", 502)

        model = payload.get("model") if isinstance(payload, dict) else None
        return LlmResponse(content=content, model=model or fallback_model)

    def _error_message(self, response_body: str) -> str | None:
        try:
            payload = json.loads(response_body)
        except json.JSONDecodeError:
            return None
        error = payload.get("error") if isinstance(payload, dict) else None
        if isinstance(error, dict):
            message = error.get("message")
            return message if isinstance(message, str) else None
        return None

    def _mock_response(self, user_prompt: str, options: GenerateWfmOptions | None = None) -> LlmResponse:
        if options and options.wfmVersion == "2.0":
            return self._mock_response_v2(user_prompt, options)

        requirement = self._extract_requirement(user_prompt)
        title = self._workflow_title(requirement)
        payload = {
            "schemaVersion": "1.0",
            "modelType": "WORKFLOW_AST",
            "workflow": {
                "id": self._slug(title),
                "title": title,
                "language": "unknown",
                "sourceRequirement": requirement,
            },
            "extensions": {"nodeKinds": [], "transitionKinds": []},
            "ast": {
                "actors": [],
                "variables": [],
                "nodes": [
                    {"id": "N1", "role": "START", "kind": "START", "title": "Start"},
                    {"id": "N2", "role": "ACTION", "kind": "REVIEW_REQUIREMENT", "title": title},
                    {"id": "N3", "role": "END", "kind": "END", "title": "End"},
                ],
                "transitions": [
                    {"id": "T1", "from": "N1", "to": "N2", "semantic": "DEFAULT"},
                    {"id": "T2", "from": "N2", "to": "N3", "semantic": "DEFAULT"},
                ],
                "annotations": [],
            },
        }
        model = options.model if options and options.model else "mock-wfm-v1"
        return LlmResponse(content=json.dumps(payload), model=model or "mock-wfm-v1")

    def _mock_response_v2(self, user_prompt: str, options: GenerateWfmOptions | None = None) -> LlmResponse:
        requirement = self._extract_requirement(user_prompt)
        payload = {
            "wfmVersion": "2.0",
            "workflowId": "mock_workflow",
            "workflowName": self._workflow_title(requirement),
            "description": None,
            "direction": "LR",
            "nodes": [
                {"id": "start", "kind": "START", "name": "Start", "description": None, "actor": None, "data": {}},
                {
                    "id": "review_requirement",
                    "kind": "ACTION",
                    "name": self._workflow_title(requirement),
                    "description": None,
                    "actor": None,
                    "data": {},
                },
                {"id": "end", "kind": "END", "name": "End", "description": None, "actor": None, "data": {}},
            ],
            "transitions": [
                {
                    "id": "t_start_review",
                    "source": "start",
                    "target": "review_requirement",
                    "label": None,
                    "condition": None,
                    "outcome": None,
                    "data": {},
                },
                {
                    "id": "t_review_end",
                    "source": "review_requirement",
                    "target": "end",
                    "label": None,
                    "condition": None,
                    "outcome": None,
                    "data": {},
                },
            ],
            "metadata": {"source": "AI", "language": "unknown", "warnings": []},
        }
        model = options.model if options and options.model else "mock-wfm-v2"
        return LlmResponse(content=json.dumps(payload), model=model or "mock-wfm-v2")

    def _extract_requirement(self, user_prompt: str) -> str:
        marker = "Requirement:"
        if marker not in user_prompt:
            return user_prompt.strip()
        return user_prompt.split(marker, 1)[1].strip()

    def _workflow_title(self, requirement: str) -> str:
        for line in requirement.splitlines():
            cleaned = line.strip().strip("-* ")
            if cleaned:
                return cleaned.removeprefix("Feature:").strip() or "Workflow"
        return "Workflow"

    def _slug(self, value: str) -> str:
        slug = "".join(char.lower() if char.isalnum() else "-" for char in value).strip("-")
        return "-".join(part for part in slug.split("-") if part) or "workflow"
