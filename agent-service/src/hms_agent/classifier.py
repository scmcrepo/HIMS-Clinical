"""Intent classification.

The classifier is an interface with two implementations on purpose. `LlmClassifier`
is what runs in production; `RuleClassifier` is deterministic, needs no network,
no API key and no residency decision, and is what the whole test suite and shadow
mode run against.

That split matters beyond testing convenience. WO-004 blocks on the model-hosting
decision (residency), and without an injectable classifier the entire graph would
be unbuildable until that decision lands. This way the graph, the routing, the
HITL interrupts and the tool calls are all built and tested now, and swapping in
a model later touches exactly one class.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any, Protocol

from .observability import CONFIDENCE, LLM_TOKENS, EventLogger
from .state import Intent

log = EventLogger(__name__)


@dataclass(frozen=True, slots=True)
class Classification:
    intent: Intent
    confidence: float
    distress: bool = False
    human_requested: bool = False


class Classifier(Protocol):
    def classify(self, text: str, *, language: str = "en") -> Classification: ...


# ── Distress and human-request detection ─────────────────────────────────────
#
# These run ahead of intent routing and are deliberately generous. The cost
# asymmetry is stark: a false escalation wastes a minute of front-desk time, a
# missed one can leave a frightened or unwell person talking to a machine. Tuned
# for recall, not precision.
#
# Note this is admin-side triage for routing to a human — it is NOT clinical
# assessment, and nothing here decides anything about care.

_DISTRESS_PATTERNS = [
    r"\b(emergency|urgent|serious|critical|severe)\b",
    r"\b(chest pain|breathless|breathing|bleeding|unconscious|collapsed|seizure|stroke)\b",
    r"\b(dying|can'?t breathe|cannot breathe|help me)\b",
    r"\b(accident|injured|fell down)\b",
    # Common code-mixed forms heard on Indian OPD lines.
    r"\b(bahut dard|taklif|ache?ha nahi|saans)\b",
    r"\b(romba kastam|mudiyala|vali)\b",
]

_HUMAN_REQUEST_PATTERNS = [
    # The object is optional and may be interposed: "connect me to a human",
    # "put me through to someone", "transfer to staff".
    (r"\b(speak|talk|connect|transfer|put)\b(?:\s+\w+){0,3}?\s+(?:to|with)\s+(?:a\s+|an\s+|the\s+)?"
     r"(human|person|someone|somebody|staff|agent|receptionist|operator)\b"),
    r"\b(real person|actual person|human being)\b",
    r"\b(customer care|supervisor|manager)\b",
    r"\bnot a (bot|machine|robot)\b",
]

# Intent signals are grouped so that a clearly worded request lights up several
# independent groups (action + subject + time) rather than one broad pattern.
# Scoring on distinct groups is what lets confidence mean something: a terse
# "book appointment" is genuinely less certain than "book an appointment with a
# doctor tomorrow", and the escalation threshold depends on that distinction
# being real rather than an artefact of how the regexes were written.
_INTENT_SIGNALS: list[tuple[Intent, list[str]]] = [
    (Intent.SCHEDULING, [
        r"\b(appointment|appoint|booking|slot|schedule|reschedule|rebook)\b",
        r"\b(book|cancel|change|move|fix)\b",
        (r"\b(doctor|dr\.?|physician|pediatrician|paediatrician|specialist|consultant|"
         r"cardiologist|dentist|gynaec|ortho)\b"),
        (r"\b(tomorrow|today|tonight|morning|evening|afternoon|next week|"
         r"monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"),
        r"\b(see|meet|consult|visit)\b",
    ]),
    (Intent.ABHA, [
        r"\babha\b",
        r"\b(ayushman|health id|health account|health card)\b",
        r"\b(aadhaar|aadhar)\b",
        r"\b(link|register|create|enrol|enroll|generate)\b",
    ]),
    (Intent.CLAIMS, [
        r"\b(insurance|insurer|policy|coverage|covered)\b",
        r"\b(claim|pre-?auth|preauthorisation|preauthorization|cashless|reimburse)\b",
        r"\b(tpa|third party|network hospital)\b",
    ]),
    (Intent.BILLING, [
        r"\b(bill|invoice|receipt|statement)\b",
        r"\b(payment|pay|due|outstanding|balance|amount|charges?)\b",
        r"\bhow much\b",
    ]),
    (Intent.SMALLTALK, [
        r"^\s*(hi|hello|hey|namaste|vanakkam|good morning|good evening)\b",
        r"^\s*(thanks|thank you|ok|okay|bye|goodbye)\b",
    ]),
]

# Distinct signal groups matched -> confidence. Calibrated so that a single weak
# hit lands below the default 0.80 threshold and therefore reaches a human.
_CONFIDENCE_BY_HITS = {0: 0.20, 1: 0.70, 2: 0.85, 3: 0.92}
_CONFIDENCE_MAX = 0.95


class RuleClassifier:
    """Deterministic classifier.

    Not a toy: it is the shadow-mode baseline and the test fixture, and its
    confidence scores are calibrated to be honest rather than flattering. A
    single weak keyword hit reports ~0.55, which is below the default 0.80
    escalation threshold — so ambiguous input goes to a human instead of being
    guessed at.
    """

    def classify(self, text: str, *, language: str = "en") -> Classification:
        lowered = (text or "").lower().strip()

        distress = any(re.search(p, lowered) for p in _DISTRESS_PATTERNS)
        human = any(re.search(p, lowered) for p in _HUMAN_REQUEST_PATTERNS)

        best_intent, best_hits = Intent.UNKNOWN, 0
        for intent, signals in _INTENT_SIGNALS:
            hits = sum(1 for p in signals if re.search(p, lowered))
            if hits > best_hits:
                best_intent, best_hits = intent, hits

        confidence = _CONFIDENCE_BY_HITS.get(min(best_hits, 3), _CONFIDENCE_MAX)

        # Very short inputs carry little signal whatever the keywords suggest.
        # "doctor" alone is not a booking request.
        if len(lowered.split()) <= 2 and best_intent is not Intent.SMALLTALK:
            confidence = min(confidence, 0.50)

        CONFIDENCE.observe(confidence)
        return Classification(
            intent=best_intent,
            confidence=confidence,
            distress=distress,
            human_requested=human,
        )


class LlmClassifier:
    """Model-backed intent classification.

    Provider-agnostic: it speaks the OpenAI-compatible chat-completions shape,
    which every India-region host worth using exposes (self-hosted vLLM, Ollama,
    Sarvam, Bhashini-adjacent gateways, or a commercial model in an Indian
    region). Point ``endpoint`` at whichever one the residency decision lands on.

    Two properties matter more than accuracy here.

    **The prompt is minimised.** Only the utterance is sent — never the patient
    id, name, ledger or appointment history. Whatever goes into a prompt reaches
    the provider and usually their logs, so the prompt is the exfiltration
    surface and it is kept as small as the task allows.

    **It degrades to rules rather than to a guess.** If the model is unreachable,
    returns malformed JSON, or times out, the ``RuleClassifier`` answers instead.
    A classifier that raises would take the whole channel down; one that invents
    a confident intent is worse, because the escalation threshold then never
    fires. The fallback keeps low confidence low, which routes the caller to a
    human — the correct behaviour when the system is degraded.
    """

    SYSTEM_PROMPT = (
        "You classify a hospital patient's message into one intent. "
        "Reply with ONLY a JSON object, no prose and no markdown fences:\n"
        '{"intent": one of ["scheduling","abha","claims","billing","smalltalk","unknown"], '
        '"confidence": 0.0-1.0, "distress": true|false, "human_requested": true|false}\n'
        "Set distress=true for medical urgency, pain, or emotional distress. "
        "Set human_requested=true if they ask for a person. "
        "Be honest about confidence: use below 0.8 when the message is ambiguous. "
        "Do not guess an intent to seem helpful."
    )

    def __init__(self, endpoint: str, model: str, api_key: str = "",
                 fallback: Classifier | None = None, timeout: float = 3.0,
                 client: Any = None) -> None:
        self._endpoint = endpoint.rstrip("/")
        self._model = model
        self._api_key = api_key
        self._fallback = fallback or RuleClassifier()
        self._timeout = timeout
        self._client = client

    def _http(self) -> Any:
        if self._client is None:
            import httpx
            self._client = httpx.Client(timeout=self._timeout)
        return self._client

    def classify(self, text: str, *, language: str = "en") -> Classification:
        if not text or not text.strip():
            return self._fallback.classify(text, language=language)
        try:
            payload = {
                "model": self._model,
                "temperature": 0,
                "max_tokens": 120,
                "messages": [
                    {"role": "system", "content": self.SYSTEM_PROMPT},
                    # The utterance only. No identifiers, no history.
                    {"role": "user", "content": text[:2000]},
                ],
            }
            headers = {"Content-Type": "application/json"}
            if self._api_key:
                headers["Authorization"] = f"Bearer {self._api_key}"

            response = self._http().post(
                f"{self._endpoint}/v1/chat/completions", json=payload, headers=headers)
            if response.status_code >= 400:
                raise RuntimeError(f"status {response.status_code}")

            body = response.json()
            content = body["choices"][0]["message"]["content"]

            usage = body.get("usage") or {}
            if usage:
                LLM_TOKENS.labels(model=self._model, direction="prompt").inc(
                    usage.get("prompt_tokens", 0))
                LLM_TOKENS.labels(model=self._model, direction="completion").inc(
                    usage.get("completion_tokens", 0))

            return self._parse(content)

        except Exception as exc:  # noqa: BLE001 - deliberate catch-all.
            # Degrade, never raise: any failure reaching the caller takes down
            # the whole channel, and the rule fallback keeps ambiguous input
            # below the escalation threshold so callers still reach a human.
            log.warning("agent.classifier.fallback",
                        error_type=type(exc).__name__, model=self._model)
            return self._fallback.classify(text, language=language)

    def _parse(self, content: str) -> Classification:
        cleaned = content.strip()
        if cleaned.startswith("```"):
            cleaned = re.sub(r"^```[a-z]*\s*|\s*```$", "", cleaned)
        data = json.loads(cleaned)

        raw_intent = str(data.get("intent", "unknown")).lower()
        try:
            intent = Intent(raw_intent)
        except ValueError:
            intent = Intent.UNKNOWN

        confidence = float(data.get("confidence", 0.0))
        confidence = min(max(confidence, 0.0), 1.0)
        # An unknown intent asserted confidently is a contradiction; clamp it so
        # it cannot slip past the escalation threshold.
        if intent is Intent.UNKNOWN:
            confidence = min(confidence, 0.4)

        CONFIDENCE.observe(confidence)
        return Classification(
            intent=intent,
            confidence=confidence,
            distress=bool(data.get("distress", False)),
            human_requested=bool(data.get("human_requested", False)),
        )


def build_classifier(endpoint: str = "", model: str = "", api_key: str = "") -> Classifier:
    """Pick a classifier from configuration.

    No endpoint configured means the rule classifier, which is a legitimate
    running mode rather than a broken one: it is what shadow mode and the test
    suite use, and it is honest about its own uncertainty.
    """
    if endpoint and model:
        return LlmClassifier(endpoint=endpoint, model=model, api_key=api_key)
    return RuleClassifier()
