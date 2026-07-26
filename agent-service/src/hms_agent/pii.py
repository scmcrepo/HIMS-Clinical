"""PII masking.

Everything that leaves this process as a log line, a metric label, a span
attribute or an LLM prompt goes through here first.

The rule this module enforces is narrow and absolute: log the surrogate id, never
the person. A patient UUID in a log line is fine and useful. A name, a phone
number, an Aadhaar or an ABHA address in the same line is a DPDP Act exposure
that no later cleanup undoes, because the line has already been shipped to Loki
and replicated into backups.

Masking is deliberately lossy and one-way. If you need to correlate, correlate on
the id.
"""

from __future__ import annotations

import re
from typing import Any

# Field names that carry personal data. Matched case-insensitively against dict
# keys after stripping underscores, so patient_name / patientName / PatientName
# all hit.
PII_FIELD_NAMES: frozenset[str] = frozenset({
    "name", "fullname", "firstname", "lastname", "middlename", "patientname",
    "temppatientname", "guardianname", "salutation",
    "phone", "mobile", "contact", "contactnumber", "temppatientphone",
    "email", "emailid",
    "address", "addressline", "street", "city", "pincode", "postalcode",
    "aadhaar", "aadhar", "aadhaarnumber",
    "abha", "abhanumber", "abhaaddress",
    "dob", "dateofbirth", "age", "temppatientage", "gender",
    "diagnosis", "notes", "transcript", "chiefcomplaint", "symptoms",
    "memberid", "policynumber", "insurancenumber",
})

# Keys that look like PII by name but are surrogate identifiers, which are the
# whole point of the logging convention.
SAFE_FIELD_NAMES: frozenset[str] = frozenset({
    "patientid", "abhaid", "contactid", "nameid", "addressid",
    "diagnosisid", "encounterid", "tenantid", "branchid",
})

_INDIAN_MOBILE = re.compile(r"(?<!\d)((?:\+?91[\-\s]?)?[6-9]\d{9})(?!\d)")
_AADHAAR = re.compile(r"(?<!\d)(\d{4}[\-\s]?\d{4}[\-\s]?\d{4})(?!\d)")
_ABHA_NUMBER = re.compile(r"(?<!\d)(\d{2}[\-\s]?\d{4}[\-\s]?\d{4}[\-\s]?\d{4})(?!\d)")
_ABHA_ADDRESS = re.compile(r"\b[\w.\-]+@(?:abdm|sbx)\b", re.IGNORECASE)
_EMAIL = re.compile(r"\b[\w.\-+]+@[\w\-]+\.[\w.\-]+\b")

REDACTED = "[REDACTED]"


def _normalise(key: str) -> str:
    return key.replace("_", "").replace("-", "").lower()


def is_pii_field(key: str) -> bool:
    """Whether a field name should be treated as personal data."""
    norm = _normalise(key)
    if norm in SAFE_FIELD_NAMES:
        return False
    if norm.endswith("id") and norm[:-2] in {n[:-2] for n in SAFE_FIELD_NAMES if n.endswith("id")}:
        return False
    return norm in PII_FIELD_NAMES


def mask_phone(value: str) -> str:
    """+919876543210 -> +91XXXXXX3210. Enough to confirm the right number, not enough to dial it."""
    digits = re.sub(r"\D", "", value)
    if len(digits) < 4:
        return REDACTED
    return f"{'X' * (len(digits) - 4)}{digits[-4:]}"


def mask_aadhaar(value: str) -> str:
    digits = re.sub(r"\D", "", value)
    if len(digits) < 4:
        return REDACTED
    return f"XXXX XXXX {digits[-4:]}"


def mask_free_text(text: str) -> str:
    """Redact identifiers embedded in free text.

    Exception messages and STT transcripts are the usual offenders: nobody
    intends to log a phone number, it just arrives inside a sentence.

    Ordering matters — ABHA numbers are 14 digits and would otherwise be partly
    consumed by the 12-digit Aadhaar pattern.
    """
    if not text:
        return text
    out = _ABHA_ADDRESS.sub(REDACTED, text)
    out = _ABHA_NUMBER.sub(REDACTED, out)
    out = _AADHAAR.sub(REDACTED, out)
    out = _INDIAN_MOBILE.sub(REDACTED, out)
    out = _EMAIL.sub(REDACTED, out)
    return out


def redact(value: Any, _depth: int = 0) -> Any:
    """Recursively redact a structure so it is safe to log.

    Dict keys that name personal data have their values replaced. Strings are
    scanned for embedded identifiers. Everything else passes through.
    """
    if _depth > 12:
        return REDACTED

    if isinstance(value, dict):
        out: dict[str, Any] = {}
        for k, v in value.items():
            if is_pii_field(str(k)):
                norm = _normalise(str(k))
                if isinstance(v, str) and norm in {"phone", "mobile", "contact",
                                                   "contactnumber", "temppatientphone"}:
                    out[k] = mask_phone(v)
                elif isinstance(v, str) and norm in {"aadhaar", "aadhar", "aadhaarnumber"}:
                    out[k] = mask_aadhaar(v)
                else:
                    out[k] = REDACTED
            else:
                out[k] = redact(v, _depth + 1)
        return out

    if isinstance(value, (list, tuple)):
        return [redact(v, _depth + 1) for v in value]

    if isinstance(value, str):
        return mask_free_text(value)

    return value
