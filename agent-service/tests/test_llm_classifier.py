"""LLM classifier: prompt hygiene, parsing, and degradation."""
import json

import httpx

from hms_agent.classifier import LlmClassifier, RuleClassifier, build_classifier
from hms_agent.state import Intent


def client_for(handler):
    return httpx.Client(transport=httpx.MockTransport(handler))


def reply(content, usage=None):
    body = {"choices": [{"message": {"content": content}}]}
    if usage:
        body["usage"] = usage
    return httpx.Response(200, json=body)


def clf(handler, **kw):
    return LlmClassifier(endpoint="http://llm.test", model="m",
                         client=client_for(handler), **kw)


class TestSelection:
    def test_no_endpoint_means_rules(self):
        assert isinstance(build_classifier(), RuleClassifier)

    def test_configured_endpoint_means_llm(self):
        assert isinstance(build_classifier("http://x", "m"), LlmClassifier)


class TestPromptHygiene:
    def test_only_the_utterance_is_sent(self):
        # Whatever goes in the prompt reaches the provider and usually their logs.
        sent = {}

        def handler(request):
            sent.update(json.loads(request.content))
            return reply('{"intent":"scheduling","confidence":0.9}')

        clf(handler).classify("book me a slot")

        user_msgs = [m for m in sent["messages"] if m["role"] == "user"]
        assert len(user_msgs) == 1
        assert user_msgs[0]["content"] == "book me a slot"

    def test_long_input_is_truncated(self):
        sent = {}

        def handler(request):
            sent.update(json.loads(request.content))
            return reply('{"intent":"unknown","confidence":0.1}')

        clf(handler).classify("x" * 9000)
        assert len(sent["messages"][-1]["content"]) <= 2000

    def test_api_key_is_sent_as_a_bearer_header(self):
        seen = {}

        def handler(request):
            seen.update(request.headers)
            return reply('{"intent":"smalltalk","confidence":0.9}')

        clf(handler, api_key="secret-key").classify("hello")
        assert seen["authorization"] == "Bearer secret-key"


class TestParsing:
    def test_parses_a_clean_response(self):
        result = clf(lambda r: reply(
            '{"intent":"claims","confidence":0.91,"distress":false,'
            '"human_requested":false}')).classify("is my insurance covered")
        assert result.intent is Intent.CLAIMS
        assert result.confidence == 0.91

    def test_strips_markdown_fences(self):
        # Models emit fenced JSON constantly despite instructions not to.
        result = clf(lambda r: reply(
            '```json\n{"intent":"billing","confidence":0.88}\n```')).classify("my bill")
        assert result.intent is Intent.BILLING

    def test_unknown_intent_cannot_be_confident(self):
        # A confidently-unknown intent would slip past the escalation threshold.
        result = clf(lambda r: reply(
            '{"intent":"unknown","confidence":0.99}')).classify("mmm")
        assert result.confidence <= 0.4

    def test_unrecognised_intent_label_becomes_unknown(self):
        result = clf(lambda r: reply(
            '{"intent":"teleportation","confidence":0.9}')).classify("beam me up")
        assert result.intent is Intent.UNKNOWN

    def test_confidence_is_clamped_to_range(self):
        result = clf(lambda r: reply(
            '{"intent":"scheduling","confidence":7.5}')).classify("book appointment doctor")
        assert 0.0 <= result.confidence <= 1.0

    def test_distress_and_human_flags_survive(self):
        result = clf(lambda r: reply(
            '{"intent":"unknown","confidence":0.3,"distress":true,'
            '"human_requested":true}')).classify("help")
        assert result.distress and result.human_requested

    def test_token_usage_is_counted(self):
        # Cost visibility from day one, not after the first surprising invoice.
        from hms_agent.observability import LLM_TOKENS
        before = LLM_TOKENS.labels(model="m", direction="prompt")._value.get()
        clf(lambda r: reply('{"intent":"smalltalk","confidence":0.9}',
                            usage={"prompt_tokens": 42, "completion_tokens": 7})).classify("hi")
        assert LLM_TOKENS.labels(model="m", direction="prompt")._value.get() == before + 42


class TestDegradation:
    def test_falls_back_when_the_model_errors(self):
        # Raising here would take the whole channel down.
        result = clf(lambda r: httpx.Response(500, json={})).classify(
            "book an appointment with a doctor tomorrow")
        assert result.intent is Intent.SCHEDULING

    def test_falls_back_on_malformed_json(self):
        result = clf(lambda r: reply("I think they want an appointment!")).classify(
            "book an appointment with a doctor tomorrow")
        assert result.intent is Intent.SCHEDULING

    def test_falls_back_on_timeout(self):
        def handler(request):
            raise httpx.TimeoutException("slow")

        result = clf(handler).classify("book an appointment with a doctor tomorrow")
        assert result.intent is Intent.SCHEDULING

    def test_fallback_keeps_ambiguous_input_below_threshold(self):
        # Degraded must still route the caller to a human, not guess confidently.
        result = clf(lambda r: httpx.Response(503, json={})).classify("uh")
        assert result.confidence < 0.8

    def test_fallback_still_detects_distress(self):
        result = clf(lambda r: httpx.Response(500, json={})).classify(
            "my father has chest pain")
        assert result.distress

    def test_empty_input_does_not_call_the_model(self):
        called = []

        def handler(request):
            called.append(1)
            return reply('{"intent":"unknown","confidence":0.1}')

        clf(handler).classify("   ")
        assert called == []
