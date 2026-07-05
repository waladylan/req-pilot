from typing import Any

from app.config import Settings
from app.schemas.request import GenerateWfmRequest
from app.schemas.response import GenerateWfmMetadata
from app.services.json_parser import JsonParser, JsonParsingError
from app.services.llm_client import LlmClient, LlmClientError
from app.services.prompt_builder_v2 import PROMPT_VERSION, PromptBuilderV2
from app.services.rule_based_generator_v2 import RuleBasedGeneratorV2
from app.services.wfm_generator import WfmServiceError
from app.services.wfm_v2_canonicalizer import WfmV2Canonicalizer
from app.services.wfm_v2_validator import WfmV2ValidationError, WfmV2Validator


class WfmV2Generator:
    def __init__(
        self,
        prompt_builder: PromptBuilderV2,
        llm_client: LlmClient,
        json_parser: JsonParser,
        rule_based_generator: RuleBasedGeneratorV2,
        canonicalizer: WfmV2Canonicalizer,
        validator: WfmV2Validator,
        settings: Settings,
    ) -> None:
        self.prompt_builder = prompt_builder
        self.llm_client = llm_client
        self.json_parser = json_parser
        self.rule_based_generator = rule_based_generator
        self.canonicalizer = canonicalizer
        self.validator = validator
        self.settings = settings

    def generate(self, request: GenerateWfmRequest, mode: str) -> tuple[dict[str, Any], GenerateWfmMetadata]:
        try:
            payload, metadata = self._draft(request, mode)
            canonical_payload, canonical_warnings = self.canonicalizer.canonicalize(payload, request.requirement)
            validation = self.validator.validate_or_raise(canonical_payload)
            warnings = [
                *metadata.warnings,
                *canonical_warnings,
                *(issue.message for issue in validation.warnings),
            ]
            metadata = metadata.model_copy(
                update={
                    "wfmVersion": "2.0",
                    "validationStatus": "PASSED",
                    "canonicalizationStatus": "PASSED",
                    "mappingStatus": "PASSED",
                    "warnings": list(dict.fromkeys(warnings)),
                }
            )
            return canonical_payload, metadata
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
                "LLM response did not contain usable WFM v2 JSON",
                502,
                {"reason": str(exception)},
            ) from exception
        except WfmV2ValidationError as exception:
            raise WfmServiceError(
                "WFM_VALIDATION_FAILED",
                "Generated WFM does not match WFM v2",
                422,
                {
                    "errors": [issue.model_dump() for issue in exception.result.errors],
                    "warnings": [issue.model_dump() for issue in exception.result.warnings],
                },
            ) from exception

    def _draft(self, request: GenerateWfmRequest, mode: str) -> tuple[dict[str, Any], GenerateWfmMetadata]:
        if mode == "MOCK":
            return self.rule_based_generator.mock(), self._metadata(
                mode="MOCK",
                wfm_source="python-wfm-v2-mock-generator",
                model="mock-wfm-v2",
            )

        if mode == "RULE_BASED":
            return self.rule_based_generator.generate(request.requirement), self._metadata(
                mode="RULE_BASED",
                wfm_source="python-wfm-v2-rule-based-generator",
                model=None,
            )

        llm_response = self.llm_client.generate(
            self.prompt_builder.system_prompt(),
            self.prompt_builder.user_prompt(request.requirement),
            request.options,
        )
        payload = self._extract_wfm_payload(self.json_parser.parse_first_object(llm_response.content))
        return payload, self._metadata(
            mode="AI",
            wfm_source="python-wfm-v2-ai-generator",
            model=llm_response.model,
        )

    def _metadata(self, mode: str, wfm_source: str, model: str | None) -> GenerateWfmMetadata:
        return GenerateWfmMetadata(
            model=model,
            promptVersion=self.prompt_builder.prompt_version or PROMPT_VERSION,
            generationMode=mode,
            wfmVersion="2.0",
            wfmSource=wfm_source,
            flowchartSource="python-wfm-v2-flowchart-mapper",
            validationStatus="PASSED",
            canonicalizationStatus="PASSED",
            normalizationStatus=None,
            mappingStatus="PASSED",
            warnings=[],
        )

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
