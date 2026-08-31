package com.hms.security.encryption;

/**
 * The one place personal data is masked for display or logging.
 *
 * <p>The compliance rule this implements is short: log the surrogate id, never
 * the person. Where an identifier genuinely has to appear — an operator
 * confirming which of a patient's three numbers a message went to — it appears
 * masked, and it is masked the same way everywhere.
 *
 * <p>A single utility rather than a mask per call site, because per-call-site
 * masking drifts: one place shows four trailing digits, another shows six, and
 * the combination of the two reconstructs the original.
 *
 * <p>Masking is <b>not</b> a licence to log personal data. Prefer the patient
 * UUID. These helpers exist for the cases where an operator genuinely cannot act
 * on a surrogate id.
 */
public final class PiiMasking {

    private PiiMasking() {}

    private static final String REDACTED = "[redacted]";

    /**
     * {@code 9876543210} → {@code ******3210}.
     *
     * <p>Four trailing digits is the convention used across Indian financial and
     * health systems, and matches what the ABHA response masking already does.
     */
    public static String phone(String value) {
        if (value == null || value.isBlank()) {
            return REDACTED;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return REDACTED;
        }
        return "*".repeat(Math.max(0, digits.length() - 4)) + digits.substring(digits.length() - 4);
    }

    /**
     * {@code ramesh.kumar@example.com} → {@code r****r@example.com}.
     *
     * <p>The domain survives because it is operationally useful (which mail
     * server, which corporate tenant) and is not personal data on its own. The
     * local part does not.
     */
    public static String email(String value) {
        if (value == null || value.isBlank()) {
            return REDACTED;
        }
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            return REDACTED;
        }
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() == 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "*".repeat(Math.max(1, local.length() - 2))
             + local.charAt(local.length() - 1) + domain;
    }

    /**
     * Aadhaar and similar 12-digit identifiers → {@code XXXX XXXX 1234}.
     *
     * <p>Aadhaar should not be stored at all. This exists for the display path
     * where a number the operator just typed is echoed back to them.
     */
    public static String aadhaar(String value) {
        if (value == null) {
            return REDACTED;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() != 12) {
            return REDACTED;
        }
        return "XXXX XXXX " + digits.substring(8);
    }

    /** ABHA number {@code 12-3456-7890-1234} → {@code XX-XXXX-XXXX-1234}. */
    public static String abhaNumber(String value) {
        if (value == null) {
            return REDACTED;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return REDACTED;
        }
        return "XX-XXXX-XXXX-" + digits.substring(digits.length() - 4);
    }

    /**
     * Any free-text personal field — name, address, clinical note.
     *
     * <p>Always fully redacted. There is no partial form of a name that is both
     * useful and safe, and a "helpful" first initial plus a date of birth in the
     * next field re-identifies the person.
     */
    public static String freeText(String value) {
        return REDACTED;
    }
}
