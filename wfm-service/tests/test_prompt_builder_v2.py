from app.services.prompt_builder_v2 import PromptBuilderV2


def test_prompt_v2_contains_requirement_and_wfm_v2_rules() -> None:
    builder = PromptBuilderV2()
    system_prompt = builder.system_prompt()
    user_prompt = builder.user_prompt("User creates a purchase request.")

    assert "User creates a purchase request." in user_prompt
    assert '"wfmVersion": "2.0"' in system_prompt
    assert "Generate WFM v2 JSON only" in system_prompt
    assert "Do not generate React Flow" in system_prompt
    assert "Do not generate Mermaid" in system_prompt
    assert "Do not generate layout positions" in system_prompt
    assert "transition.data.loop = true" in system_prompt
    assert "Manager Approval" in system_prompt
    assert "Amount > 1000?" in system_prompt
