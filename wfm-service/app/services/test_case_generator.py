from collections import defaultdict
from typing import Any

from app.schemas.request import GenerateTestCasesOptions, GenerateTestCasesRequest
from app.schemas.response import GenerateTestCasesResponse
from app.schemas.test_case import (
    GeneratedTestCase,
    GenerateTestCasesMetadata,
    TestCaseCoverage,
    TestCaseSet,
    TestCaseSetMetadata,
    TestCaseSourcePath,
    TestCaseStep,
)
from app.services.wfm_generator import WfmServiceError
from app.services.wfm_v2_validator import WfmV2Validator


class WfmV2TestCaseGenerator:
    TERMINAL_KINDS = {"END", "ERROR"}
    CYCLE_ERROR_CODES = {"CYCLE_REQUIRES_LOOP_MARKER"}

    def __init__(self, validator: WfmV2Validator) -> None:
        self.validator = validator

    def generate(self, request: GenerateTestCasesRequest) -> GenerateTestCasesResponse:
        wfm = request.wfm
        options = request.options or GenerateTestCasesOptions()
        warnings = self._validate(wfm)

        nodes = [node for node in wfm.get("nodes", []) if isinstance(node, dict)]
        transitions = [transition for transition in wfm.get("transitions", []) if isinstance(transition, dict)]
        node_by_id = {str(node.get("id")): node for node in nodes}
        outgoing = self._outgoing(transitions)
        start = self._start_node(nodes)

        paths = self._enumerate_paths(start, node_by_id, outgoing, max_depth=max(len(nodes) * 2, 1), warnings=warnings)
        paths = self._sort_paths(paths)
        if not options.includeNegativeCases:
            paths = [path for path in paths if not self._is_negative_path(path, node_by_id)]
        paths = paths[: options.maxCases]

        test_cases = [
            self._to_test_case(index + 1, path, node_by_id, wfm)
            for index, path in enumerate(paths)
        ]
        coverage = self._coverage(nodes, transitions, paths, outgoing, warnings)
        metadata = GenerateTestCasesMetadata(
            sourceWfmVersion="2.0",
            strategy=options.strategy,
            warnings=warnings,
        )
        test_case_set = TestCaseSet(
            workflowId=str(wfm.get("workflowId") or "workflow"),
            workflowName=str(wfm.get("workflowName") or "Workflow"),
            testCases=test_cases,
            coverage=coverage,
            metadata=TestCaseSetMetadata(
                strategy=options.strategy,
                generationMode=self._generation_mode(wfm),
                warnings=warnings,
            ),
        )
        return GenerateTestCasesResponse(testCaseSet=test_case_set, metadata=metadata)

    def _validate(self, wfm: dict[str, Any]) -> list[str]:
        if not isinstance(wfm, dict):
            raise self._error("WFM payload is required")
        if wfm.get("wfmVersion") != "2.0":
            raise self._error("Only WFM v2.0 is supported for test case generation")

        result = self.validator.validate(wfm)
        blocking_errors = [error for error in result.errors if error.code not in self.CYCLE_ERROR_CODES]
        if blocking_errors:
            raise WfmServiceError(
                "TEST_CASE_GENERATION_FAILED",
                "Invalid WFM v2 payload",
                422,
                {"errors": [error.model_dump() for error in blocking_errors]},
            )
        warnings = [warning.message for warning in result.warnings]
        warnings.extend(error.message for error in result.errors if error.code in self.CYCLE_ERROR_CODES)
        return warnings

    def _start_node(self, nodes: list[dict[str, Any]]) -> dict[str, Any]:
        starts = [node for node in nodes if self._kind(node) == "START"]
        if len(starts) != 1:
            raise self._error("WFM v2 must contain exactly one START node")
        return starts[0]

    def _outgoing(self, transitions: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
        outgoing: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for transition in transitions:
            outgoing[str(transition.get("source"))].append(transition)
        return outgoing

    def _enumerate_paths(
        self,
        start: dict[str, Any],
        node_by_id: dict[str, dict[str, Any]],
        outgoing: dict[str, list[dict[str, Any]]],
        max_depth: int,
        warnings: list[str],
    ) -> list[list[dict[str, Any]]]:
        paths: list[list[dict[str, Any]]] = []
        start_id = str(start.get("id"))

        def visit(node_id: str, path: list[dict[str, Any]], seen: set[str]) -> None:
            node = node_by_id.get(node_id)
            if node is None:
                return
            next_path = [*path, {"nodeId": node_id}]
            if self._kind(node) in self.TERMINAL_KINDS or not outgoing.get(node_id) or len(next_path) >= max_depth:
                paths.append(next_path)
                return
            for transition in outgoing[node_id]:
                target = str(transition.get("target"))
                transition_id = str(transition.get("id"))
                if target in seen:
                    warnings.append(f"Cycle skipped at transition '{transition_id}'.")
                    paths.append([*next_path, {"transitionId": transition_id, "cycleSkipped": True}])
                    continue
                visit(target, [*next_path, {"transitionId": transition_id}], {*seen, target})

        visit(start_id, [], {start_id})
        return paths

    def _sort_paths(self, paths: list[list[dict[str, Any]]]) -> list[list[dict[str, Any]]]:
        return sorted(
            paths,
            key=lambda path: (
                len(self._transition_ids(path)),
                "|".join(self._transition_ids(path)),
            ),
        )

    def _to_test_case(
        self,
        number: int,
        path: list[dict[str, Any]],
        node_by_id: dict[str, dict[str, Any]],
        wfm: dict[str, Any],
    ) -> GeneratedTestCase:
        node_ids = self._node_ids(path)
        transition_ids = self._transition_ids(path)
        transitions_by_id = {
            str(transition.get("id")): transition
            for transition in wfm.get("transitions", [])
            if isinstance(transition, dict)
        }
        branch_phrases = [
            self._branch_phrase(transitions_by_id[transition_id], node_by_id)
            for transition_id in transition_ids
            if transition_id in transitions_by_id and self._branch_phrase(transitions_by_id[transition_id], node_by_id)
        ]
        branch_phrases = self._unique(branch_phrases)
        final_node = node_by_id.get(node_ids[-1]) if node_ids else None
        title_suffix = " and ".join(branch_phrases) if branch_phrases else self._name(final_node)
        steps = self._steps(node_ids, transition_ids, node_by_id, transitions_by_id)
        tags = self._tags(node_ids, branch_phrases, node_by_id)
        expected_result = f"The workflow reaches {self._name(final_node)}."
        return GeneratedTestCase(
            id=f"TC-{number:03d}",
            title=f"{wfm.get('workflowName') or 'Workflow'} - {title_suffix}",
            priority=self._priority(final_node, branch_phrases),
            sourcePath=TestCaseSourcePath(nodeIds=node_ids, transitionIds=transition_ids),
            preconditions=self._preconditions(node_ids, node_by_id),
            steps=steps,
            expectedResult=expected_result,
            testData={},
            tags=tags,
        )

    def _steps(
        self,
        node_ids: list[str],
        transition_ids: list[str],
        node_by_id: dict[str, dict[str, Any]],
        transitions_by_id: dict[str, dict[str, Any]],
    ) -> list[TestCaseStep]:
        steps: list[TestCaseStep] = []
        transition_after_node = {
            node_ids[index]: transitions_by_id.get(transition_ids[index])
            for index in range(min(len(node_ids) - 1, len(transition_ids)))
        }
        for node_id in node_ids:
            node = node_by_id.get(node_id)
            if node is None or self._kind(node) in {"START", "END"}:
                continue
            transition = transition_after_node.get(node_id)
            branch = self._meaning(transition)
            action, expected = self._step_copy(node, branch)
            steps.append(TestCaseStep(stepNo=len(steps) + 1, action=action, expectedResult=expected))
        return steps

    def _step_copy(self, node: dict[str, Any], branch: str | None) -> tuple[str, str]:
        name = self._name(node)
        actor = self._text(node.get("actor"))
        kind = self._kind(node)
        prefix = f"{actor} " if actor else ""
        if kind == "APPROVAL":
            return f"{prefix}reviews {name}", f"{name} review is completed."
        if kind == "DECISION":
            return f"Verify {name}", f"Branch follows {branch or name}."
        if kind == "NOTIFICATION":
            return f"Check notification {name}", f"Notification '{name}' is shown."
        if kind == "WAIT":
            return f"Wait for {name}", f"Workflow continues after {name}."
        if kind == "ERROR":
            return f"Trigger {name}", f"Error handling for {name} is shown."
        return f"{prefix}{name}", f"{name} is completed."

    def _coverage(
        self,
        nodes: list[dict[str, Any]],
        transitions: list[dict[str, Any]],
        paths: list[list[dict[str, Any]]],
        outgoing: dict[str, list[dict[str, Any]]],
        warnings: list[str],
    ) -> TestCaseCoverage:
        node_ids = [str(node.get("id")) for node in nodes]
        transition_ids = [str(transition.get("id")) for transition in transitions]
        covered_node_ids = self._unique(node_id for path in paths for node_id in self._node_ids(path))
        covered_transition_ids = self._unique(
            transition_id for path in paths for transition_id in self._transition_ids(path)
        )
        branch_coverage = [
            {
                "nodeId": node_id,
                "transitionIds": [str(transition.get("id")) for transition in node_transitions],
                "coveredTransitionIds": [
                    str(transition.get("id"))
                    for transition in node_transitions
                    if str(transition.get("id")) in covered_transition_ids
                ],
            }
            for node_id, node_transitions in outgoing.items()
            if len(node_transitions) > 1
        ]
        return TestCaseCoverage(
            nodeCount=len(nodes),
            transitionCount=len(transitions),
            coveredNodeIds=covered_node_ids,
            coveredTransitionIds=covered_transition_ids,
            uncoveredNodeIds=[node_id for node_id in node_ids if node_id not in covered_node_ids],
            uncoveredTransitionIds=[
                transition_id for transition_id in transition_ids if transition_id not in covered_transition_ids
            ],
            branchCoverage=branch_coverage,
            warnings=warnings,
        )

    def _preconditions(self, node_ids: list[str], node_by_id: dict[str, dict[str, Any]]) -> list[str]:
        actors = self._unique(
            self._text(node_by_id[node_id].get("actor"))
            for node_id in node_ids
            if node_id in node_by_id and self._text(node_by_id[node_id].get("actor"))
        )
        preconditions = ["Workflow data is available."]
        preconditions.extend(f"{actor} can perform assigned workflow actions." for actor in actors)
        return preconditions

    def _tags(self, node_ids: list[str], branch_phrases: list[str], node_by_id: dict[str, dict[str, Any]]) -> list[str]:
        kinds = {self._kind(node_by_id[node_id]) for node_id in node_ids if node_id in node_by_id}
        tags = []
        if any("reject" in phrase.lower() or "error" in phrase.lower() for phrase in branch_phrases) or "ERROR" in kinds:
            tags.append("negative")
        else:
            tags.append("success")
        if "APPROVAL" in kinds:
            tags.append("approval")
        if "DECISION" in kinds:
            tags.append("decision")
        if "ERROR" in kinds:
            tags.append("error")
        if "NOTIFICATION" in kinds:
            tags.append("notification")
        if "USER_TASK" in kinds:
            tags.append("user-task")
        if "SYSTEM_TASK" in kinds:
            tags.append("system")
        return tags

    def _priority(self, final_node: dict[str, Any] | None, branch_phrases: list[str]) -> str:
        final_text = self._name(final_node).lower()
        branch_text = " ".join(branch_phrases).lower()
        if "reject" in final_text or "error" in final_text or "reject" in branch_text:
            return "HIGH"
        if "approved" in final_text or "success" in branch_text:
            return "HIGH"
        return "MEDIUM"

    def _is_negative_path(self, path: list[dict[str, Any]], node_by_id: dict[str, dict[str, Any]]) -> bool:
        text = " ".join(self._name(node_by_id[node_id]) for node_id in self._node_ids(path) if node_id in node_by_id)
        return "reject" in text.lower() or "error" in text.lower()

    def _branch_phrase(self, transition: dict[str, Any], node_by_id: dict[str, dict[str, Any]]) -> str:
        meaning = self._meaning(transition)
        if not meaning:
            return ""
        source_name = self._name(node_by_id.get(str(transition.get("source")))).replace("?", "")
        normalized = meaning.lower()
        actor = source_name.split()[0] if source_name else ""
        if normalized in {"approved", "approve", "yes"} and "approved" in source_name.lower():
            return f"{actor} approves".strip()
        if normalized in {"rejected", "reject", "no"} and "approved" in source_name.lower():
            return f"{actor} rejects".strip()
        return meaning

    def _meaning(self, transition: dict[str, Any] | None) -> str | None:
        if transition is None:
            return None
        return (
            self._text(transition.get("condition"))
            or self._text(transition.get("outcome"))
            or self._text(transition.get("label"))
        )

    def _generation_mode(self, wfm: dict[str, Any]) -> str:
        metadata = wfm.get("metadata")
        if isinstance(metadata, dict):
            source = self._text(metadata.get("source"))
            if source:
                return source
        return "RULE_BASED"

    def _node_ids(self, path: list[dict[str, Any]]) -> list[str]:
        return [str(item["nodeId"]) for item in path if "nodeId" in item]

    def _transition_ids(self, path: list[dict[str, Any]]) -> list[str]:
        return [str(item["transitionId"]) for item in path if "transitionId" in item]

    def _kind(self, node: dict[str, Any] | None) -> str:
        return str((node or {}).get("kind") or "").upper()

    def _name(self, node: dict[str, Any] | None) -> str:
        return str((node or {}).get("name") or (node or {}).get("id") or "Workflow end")

    def _text(self, value: Any) -> str | None:
        return value.strip() if isinstance(value, str) and value.strip() else None

    def _unique(self, values) -> list[str]:  # type: ignore[no-untyped-def]
        unique_values: list[str] = []
        for value in values:
            if value and value not in unique_values:
                unique_values.append(value)
        return unique_values

    def _error(self, message: str) -> WfmServiceError:
        return WfmServiceError("TEST_CASE_GENERATION_FAILED", message, 422, {})
