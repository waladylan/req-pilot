from typing import Any

from app.config import Settings
from app.schemas.request import GenerateWfmRequest
from app.schemas.response import GenerateWfmMetadata, GenerateWfmResponse
from app.schemas.wfm import WfmDocument
from app.services.json_parser import JsonParser, JsonParsingError
from app.services.llm_client import LlmClient, LlmClientError
from app.services.prompt_builder import PromptBuilder
from app.services.wfm_validator import WfmValidationError, WfmValidator


class WfmServiceError(RuntimeError):
    def __init__(self, code: str, message: str, status_code: int = 502, details: dict[str, Any] | None = None) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
        self.details = details or {}


class WfmGenerator:
    def __init__(
        self,
        prompt_builder: PromptBuilder,
        llm_client: LlmClient,
        json_parser: JsonParser,
        validator: WfmValidator,
        settings: Settings,
    ) -> None:
        self.prompt_builder = prompt_builder
        self.llm_client = llm_client
        self.json_parser = json_parser
        self.validator = validator
        self.settings = settings

    def generate(self, request: GenerateWfmRequest) -> GenerateWfmResponse:
        try:
            llm_response = self.llm_client.generate(
                self.prompt_builder.system_prompt(),
                self.prompt_builder.user_prompt(request.requirement),
                request.options,
            )
            payload = self._extract_wfm_payload(self.json_parser.parse_first_object(llm_response.content))
            warnings = self.validator.validate(payload)
            return GenerateWfmResponse(
                wfm=WfmDocument(**payload),
                metadata=GenerateWfmMetadata(
                    model=llm_response.model,
                    promptVersion=self.prompt_builder.prompt_version,
                    validationStatus="PASSED",
                    warnings=warnings,
                ),
            )
        except LlmClientError as exception:
            raise WfmServiceError(
                "WFM_GENERATION_FAILED",
                "Unable to generate WFM from LLM provider",
                self._status_code(exception.status_code),
                {"reason": str(exception), "providerStatus": exception.status_code},
            ) from exception
        except JsonParsingError as exception:
            raise WfmServiceError(
                "WFM_GENERATION_FAILED",
                "LLM response did not contain usable WFM JSON",
                502,
                {"reason": str(exception)},
            ) from exception
        except WfmValidationError as exception:
            raise WfmServiceError(
                "WFM_VALIDATION_FAILED",
                "Generated WFM does not match WFM v1",
                422,
                {"errors": exception.errors},
            ) from exception

    def _extract_wfm_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        nested = payload.get("wfm")
        if isinstance(nested, dict):
            return nested
        return payload

    def _status_code(self, provider_status: int | None) -> int:
        if provider_status == 401:
            return 401
        if provider_status == 402:
            return 402
        if provider_status == 408:
            return 504
        if provider_status and 400 <= provider_status < 500:
            return 502
        return 503
