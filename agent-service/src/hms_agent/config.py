"""Configuration and the data-residency guard.

The residency check is not decoration. DPDP treats health data as sensitive and
ABDM requires it to stay within India; an LLM or STT call that leaves the country
carrying patient data is a compliance failure regardless of how good the model
is. Because that failure is silent — everything works, nobody notices — the check
belongs in code that runs at startup rather than in a policy document.
"""

from __future__ import annotations

from typing import Literal

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class ResidencyViolation(RuntimeError):
    """Raised when configuration would send patient data out of India."""


# Providers whose India-region endpoints are known-good. Extend deliberately,
# with evidence, not because something needs to work today.
INDIA_REGION_HOSTS: frozenset[str] = frozenset({
    "bhashini.gov.in",
    "dhruva.bhashini.gov.in",
    "healthidsbx.abdm.gov.in",
    "dev.abdm.gov.in",
    "abdm.gov.in",
    "apisbx.abdm.gov.in",
})


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="HMS_AGENT_", env_file=".env", extra="ignore")

    environment: Literal["dev", "staging", "prod"] = "dev"

    # ── HMS agent gateway (WO-001). The agent never touches Postgres directly;
    # every read and write goes through here so tenant filters, RBAC and audit
    # apply to agent actions exactly as they do to a receptionist's.
    hms_base_url: str = "http://localhost:8080/api"
    hms_agent_token: str = Field(default="", repr=False)
    hms_timeout_seconds: float = 10.0
    hms_max_retries: int = 2

    # ── Model. Left blank deliberately: choosing a provider is a residency
    # decision, and WO-004 blocks rather than guesses.
    llm_provider: str = ""
    llm_model: str = ""
    llm_endpoint: str = ""
    llm_api_key: str = Field(default="", repr=False)

    # ── Behaviour
    confidence_threshold: float = 0.80
    shadow_mode: bool = True
    hitl_timeout_seconds: int = 1800
    max_turns_per_run: int = 25

    # ── WhatsApp (W-002). Empty means the channel is not wired; the webhook
    # still verifies signatures if a secret is present.
    whatsapp_phone_number_id: str = ""
    whatsapp_access_token: str = Field(default="", repr=False)
    whatsapp_app_secret: str = Field(default="", repr=False)
    whatsapp_verify_token: str = Field(default="", repr=False)

    # ── Voice (V-002)
    exotel_account_sid: str = ""
    exotel_api_key: str = Field(default="", repr=False)
    exotel_api_token: str = Field(default="", repr=False)
    stt_endpoint: str = ""
    stt_api_key: str = Field(default="", repr=False)
    tts_endpoint: str = ""
    tts_api_key: str = Field(default="", repr=False)

    # ── Residency
    enforce_data_residency: bool = True

    @field_validator("confidence_threshold")
    @classmethod
    def _threshold_in_range(cls, v: float) -> float:
        if not 0.0 <= v <= 1.0:
            raise ValueError("confidence_threshold must be between 0 and 1")
        return v

    def assert_residency(self) -> None:
        """Fail fast if an outbound endpoint would take patient data offshore."""
        if not self.enforce_data_residency:
            return
        # STT and TTS carry patient speech, which is health data the moment a
        # symptom is described — so they are checked exactly like the model.
        for label, url in (("llm_endpoint", self.llm_endpoint),
                           ("stt_endpoint", self.stt_endpoint),
                           ("tts_endpoint", self.tts_endpoint)):
            if not url:
                continue
            host = url.split("://")[-1].split("/")[0].split(":")[0].lower()
            if not any(host == h or host.endswith("." + h) for h in INDIA_REGION_HOSTS):
                raise ResidencyViolation(
                    f"{label} host {host!r} is not on the India-region allowlist. "
                    "Patient data must not leave India (DPDP Act / ABDM). "
                    "Add the host to INDIA_REGION_HOSTS only with evidence of an "
                    "in-country deployment, or set enforce_data_residency=False "
                    "for a test that carries no real patient data."
                )


def load_settings(**overrides: object) -> Settings:
    s = Settings(**overrides)  # type: ignore[arg-type]
    s.assert_residency()
    return s
