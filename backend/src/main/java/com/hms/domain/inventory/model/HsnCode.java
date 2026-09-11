package com.hms.domain.inventory.model;

import java.util.regex.Pattern;

/**
 * HSN code format rules for {@link InventoryItem#getHsnCode()}.
 *
 * <p>GST reporting groups outward supplies by HSN at 4, 6 or 8 digits — which level
 * depends on the hospital's turnover — so a code of any other shape cannot be summarised
 * and silently lands in the report's "Unclassified" bucket.
 *
 * <p>The guard is deliberately applied only to codes being <em>written or changed</em>,
 * never to codes merely being read back. Existing rows hold values such as
 * {@code 09018}, {@code 009018} and {@code 090183990}; rejecting those on every save
 * would block edits to an item's price or stock level until someone had researched its
 * correct HSN. Stopping new bad codes is a change we can ship today, cleaning the
 * backlog is the hospital's pharmacy team's call, and the two are decoupled on purpose.
 */
public final class HsnCode {

    private HsnCode() {}

    /** 4, 6 or 8 digits — the three levels GST accepts. */
    private static final Pattern VALID = Pattern.compile("\\d{4}|\\d{6}|\\d{8}");

    public static final String RULE = "HSN code must be 4, 6 or 8 digits";

    /** Blank is valid: an item may legitimately have no code recorded yet. */
    public static boolean isValid(String code) {
        return code == null || code.isBlank() || VALID.matcher(code.trim()).matches();
    }

    /**
     * Throws if {@code code} is malformed, naming the offending value so the operator
     * can see what was rejected rather than guessing.
     */
    public static void requireValid(String code) {
        if (!isValid(code)) {
            throw new com.hms.exception.BusinessRuleViolationException(
                RULE + " — got '" + code.trim() + "'");
        }
    }

    /** True when {@code incoming} differs from {@code stored}, treating null and blank alike. */
    public static boolean isChanged(String stored, String incoming) {
        String a = stored   == null ? "" : stored.trim();
        String b = incoming == null ? "" : incoming.trim();
        return !a.equals(b);
    }

    /** Digits only, no other cleanup — the shape most malformed codes already have. */
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /**
     * A correction for {@code code}, or {@code null} when none can be offered safely.
     *
     * <p>The only transformation attempted is stripping leading zeros, and only when the
     * result lands exactly on 4, 6 or 8 digits: {@code 09018} and {@code 009018} both
     * become {@code 9018}, {@code 090183990} becomes {@code 90183990}. Anything else —
     * a 5-digit code that stays 5 digits, a code with letters, a 7-digit code with no
     * leading zero — returns null.
     *
     * <p>The restraint is the point. Padding a short code, truncating a long one, or
     * guessing at a chapter would all produce a plausible-looking code that is wrong,
     * and a wrong HSN misstates tax on every future sale of that item. A suggestion is
     * a starting point for the pharmacy team, never an answer, so nothing here is
     * applied without someone approving it.
     */
    public static String suggest(String code) {
        if (code == null) return null;
        String trimmed = code.trim();
        if (trimmed.isEmpty() || isValid(trimmed)) return null;
        if (!DIGITS.matcher(trimmed).matches()) return null;

        String stripped = trimmed.replaceFirst("^0+", "");
        return isValid(stripped) && !stripped.isEmpty() ? stripped : null;
    }
}
