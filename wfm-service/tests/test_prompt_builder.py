from app.services.prompt_builder import PromptBuilder


def test_prompt_builder_includes_requirement_and_version() -> None:
    builder = PromptBuilder("test-version")

    assert "Feature: Login" in builder.user_prompt("Feature: Login")
    assert "Prompt version: test-version" in builder.system_prompt()
    assert "WORKFLOW_AST" in builder.system_prompt()


def test_prompt_builder_constrains_output_to_wfm_json_only() -> None:
    system_prompt = PromptBuilder("test-version").system_prompt()

    assert "Return only valid JSON" in system_prompt
    assert "WFM is a Workflow AST" in system_prompt
    assert "Do not generate:" in system_prompt
    assert "- React Flow" in system_prompt
    assert "- Mermaid" in system_prompt
    assert "- layout" in system_prompt
    assert "- position" in system_prompt
    assert "- test cases" in system_prompt
    assert "flowchart generation" not in system_prompt.lower()
