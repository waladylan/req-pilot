import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    llm_provider: str
    openai_api_key: str
    openai_model: str
    openai_base_url: str
    openrouter_api_key: str
    openrouter_model: str
    openrouter_base_url: str
    request_timeout_seconds: float
    max_output_tokens: int
    temperature: float
    prompt_version: str
    default_wfm_version: str
    port: int

    @staticmethod
    def from_environment() -> "Settings":
        return Settings(
            llm_provider=os.getenv("LLM_PROVIDER", "openrouter").strip().lower(),
            openai_api_key=os.getenv("OPENAI_API_KEY", ""),
            openai_model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
            openai_base_url=os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1"),
            openrouter_api_key=os.getenv("OPENROUTER_API_KEY", ""),
            openrouter_model=os.getenv("OPENROUTER_MODEL", os.getenv("OPENROUTER_DEFAULT_MODEL", "deepseek/deepseek-chat")),
            openrouter_base_url=os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"),
            request_timeout_seconds=float(os.getenv("LLM_TIMEOUT_SECONDS", "60")),
            max_output_tokens=int(os.getenv("LLM_MAX_OUTPUT_TOKENS", "4096")),
            temperature=float(os.getenv("LLM_TEMPERATURE", "0.2")),
            prompt_version=os.getenv("WFM_PROMPT_VERSION", "wfm-v1-python-001"),
            default_wfm_version=os.getenv("WFM_DEFAULT_VERSION", "2.0"),
            port=int(os.getenv("WFM_SERVICE_PORT", "8001")),
        )

    def model_for_provider(self, requested_model: str | None = None) -> str:
        if requested_model:
            return requested_model
        if self.llm_provider == "openai":
            return self.openai_model
        if self.llm_provider == "openrouter":
            return self.openrouter_model
        return requested_model or self.openrouter_model

    def api_key_for_provider(self) -> str:
        if self.llm_provider == "openai":
            return self.openai_api_key
        if self.llm_provider == "openrouter":
            return self.openrouter_api_key
        return ""

    def base_url_for_provider(self) -> str:
        if self.llm_provider == "openai":
            return self.openai_base_url
        if self.llm_provider == "openrouter":
            return self.openrouter_base_url
        return ""
