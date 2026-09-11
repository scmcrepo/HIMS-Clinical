package com.hms.domain.catalog.model;

import java.util.regex.Pattern;

/**
 * SAC (Services Accounting Code) format rules.
 *
 * <p>Unlike HSN, which GST accepts at 4, 6 or 8 digits, SAC is always exactly six —
 * healthcare services sit in the {@code 9993xx} range. There is no turnover-based
 * shortening, so a code of any other length cannot be reported at all.
 *
 * <p>Blank is permitted. Services predate this field entirely, and requiring a code
 * on every save would block routine price edits across the whole service master
 * before anyone has classified anything.
 *
 * @see com.hms.domain.inventory.model.HsnCode the equivalent for pharmacy items
 */
public final class SacCode {

    private SacCode() {}

    private static final Pattern VALID = Pattern.compile("\\d{6}");

    public static final String RULE = "SAC code must be exactly 6 digits";

    public static boolean isValid(String code) {
        return code == null || code.isBlank() || VALID.matcher(code.trim()).matches();
    }

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
}
