from typing import Any

from app.config import Settings
from app.schemas.request import GenerateWfmRequest
from app.schemas.response import GenerateWfmMetadata, GenerateWorkflowResponse
from app.schemas.wfm import WfmDocument
from app.services.flowchart_mapper import FlowchartMapper
from app.services.rule_based_generator import RuleBasedGenerator
from app.services.wfm_generator import WfmGenerator, WfmServiceError
from app.services.wfm_normalizer import WfmNormalizer
from app.services.wfm_validator import WfmValidationError, WfmValidator
from app.services.wfm_v2_generator import WfmV2Generator


class WorkflowGenerator:
    MODES = {"AI", "RULE_BASED", "MOCK"}

    def __init__(
        self,
        wfm_generator: WfmGenerator,
        rule_based_generator: RuleBasedGenerator,
        wfm_v2_generator: WfmV2Generator,
        normalizer: WfmNormalizer,
        validator: WfmValidator,
        flowchart_mapper: FlowchartMapper,
        settings: Settings,
    ) -> None:
        self.wfm_generator = wfm_generator
        self.rule_based_generator = rule_based_generator
        self.wfm_v2_generator = wfm_v2_generator
        self.normalizer = normalizer
        self.validator = validator
        self.flowchart_mapper = flowchart_mapper
        self.settings = settings

    def generate(self, request: GenerateWfmRequest) -> GenerateWorkflowResponse:
        mode = self._mode(request)
        if self._wfm_version(request) == "2.0":
            return self._generate_v2(request, mode)

        wfm_payload, metadata = self._generate_wfm(mode, request)

        try:
            normalized_payload = self.normalizer.normalize(wfm_payload, request.requirement)
            warnings = [*metadata.warnings, *self.validator.validate(normalized_payload)]
            wfm = WfmDocument(**normalized_payload)
            flowchart = self.flowchart_mapper.map(wfm)
        except WfmValidationError as exception:
            raise WfmServiceError(
                "WFM_VALIDATION_FAILED",
                "Generated WFM does not match WFM v1",
                422,
                {"errors": exception.errors},
            ) from exception
        except Exception as exception:
            raise WfmServiceError(
                "WORKFLOW_MAPPING_FAILED",
                "Unable to map WFM to flowchart",
                502,
                {"reason": str(exception)},
            ) from exception

        return GenerateWorkflowResponse(
            wfm=wfm,
            flowchart=flowchart,
            metadata=metadata.model_copy(
                update={
                    "validationStatus": "PASSED",
                    "normalizationStatus": "PASSED",
                    "mappingStatus": "PASSED",
                    "flowchartSource": "python-flowchart-mapper",
                    "warnings": list(dict.fromkeys(warnings)),
                }
            ),
        )

    def _generate_v2(self, request: GenerateWfmRequest, mode: str) -> GenerateWorkflowResponse:
        wfm_payload, metadata = self.wfm_v2_generator.generate(request, mode)

        try:
            flowchart = self.flowchart_mapper.map(wfm_payload)
        except Exception as exception:
            raise WfmServiceError(
                "WORKFLOW_MAPPING_FAILED",
                "Unable to map WFM v2 to flowchart",
                502,
                {"reason": str(exception)},
            ) from exception

        return GenerateWorkflowResponse(
            wfm=wfm_payload,
            flowchart=flowchart,
            metadata=metadata.model_copy(
                update={
                    "wfmVersion": "2.0",
                    "canonicalizationStatus": metadata.canonicalizationStatus or "PASSED",
                    "validationStatus": "PASSED",
                    "mappingStatus": "PASSED",
                    "flowchartSource": "python-wfm-v2-flowchart-mapper",
                }
            ),
        )

    def _generate_wfm(self, mode: str, request: GenerateWfmRequest) -> tuple[dict[str, Any], GenerateWfmMetadata]:
        if mode == "MOCK":
            return self.rule_based_generator.generate(request.requirement), self._metadata(
                mode="MOCK",
                wfm_source="python-mock-generator",
                model="mock-wfm-v1",
            )

        if mode == "RULE_BASED":
            return self.rule_based_generator.generate(request.requirement), self._metadata(
                mode="RULE_BASED",
                wfm_source="python-rule-based-generator",
                model=None,
            )

        response = self.wfm_generator.generate(request)
        return response.wfm.model_dump(by_alias=True, exclude_none=True), self._metadata(
            mode="AI",
            wfm_source="python-ai-generator",
            model=response.metadata.model,
            prompt_version=response.metadata.promptVersion,
            warnings=response.metadata.warnings,
        )

    def _metadata(
        self,
        mode: str,
        wfm_source: str,
        model: str | None,
        prompt_version: str | None = None,
        warnings: list[str] | None = None,
    ) -> GenerateWfmMetadata:
        return GenerateWfmMetadata(
            model=model,
            promptVersion=prompt_version or self.settings.prompt_version,
            generationMode=mode,
            wfmSource=wfm_source,
            flowchartSource="python-flowchart-mapper",
            validationStatus="PASSED",
            normalizationStatus="PASSED",
            mappingStatus="PASSED",
            warnings=warnings or [],
        )

    def _mode(self, request: GenerateWfmRequest) -> str:
        raw_mode = request.options.generationMode if request.options else None
        mode = (raw_mode or "AI").strip().upper().replace("-", "_")
        if mode not in self.MODES:
            raise WfmServiceError(
                "UNSUPPORTED_GENERATION_MODE",
                "Unsupported workflow generation mode",
                400,
                {"generationMode": raw_mode},
            )
        return mode

    def _wfm_version(self, request: GenerateWfmRequest) -> str:
        raw_version = request.options.wfmVersion if request.options else None
        return (raw_version or self.settings.default_wfm_version or "1.0").strip()
