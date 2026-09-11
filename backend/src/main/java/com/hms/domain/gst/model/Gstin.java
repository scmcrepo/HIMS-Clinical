package com.hms.domain.gst.model;

import java.util.regex.Pattern;

/**
 * GSTIN format, checksum, and the state code that drives place of supply.
 *
 * <p>A GSTIN is 15 characters: a two-digit state code, a ten-character PAN, an entity
 * number, a literal {@code Z}, and a mod-36 check character. The checksum is verified
 * here rather than only the shape — a GSTIN that merely looks right but fails its own
 * check digit is a typo, and it will be rejected by the GST portal after the hospital
 * has already built a return around it.
 */
public final class Gstin {

    private Gstin() {}

    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final Pattern SHAPE =
        Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    public static final String RULE =
        "GSTIN must be 15 characters in the form 22AAAAA0000A1Z5 and pass its check digit";

    /** Blank is allowed — a hospital may not have entered its GSTIN yet. */
    public static boolean isValid(String gstin) {
        if (gstin == null || gstin.isBlank()) return true;
        String g = normalise(gstin);
        return SHAPE.matcher(g).matches() && g.charAt(14) == checkDigit(g);
    }

    public static void requireValid(String gstin) {
        if (!isValid(gstin)) {
            throw new com.hms.exception.BusinessRuleViolationException(
                RULE + " — got '" + gstin.trim() + "'");
        }
    }

    /** Upper-cased and stripped of the spaces people paste in from certificates. */
    public static String normalise(String gstin) {
        return gstin == null ? null : gstin.trim().replace(" ", "").toUpperCase();
    }

    /**
     * The two-digit state code, or {@code null} when the GSTIN is absent or malformed.
     *
     * <p>This is the hospital's own state, and therefore the default place of supply
     * for anything sold or provided at the establishment.
     */
    public static String stateCode(String gstin) {
        if (gstin == null || gstin.isBlank()) return null;
        String g = normalise(gstin);
        if (!SHAPE.matcher(g).matches()) return null;
        return g.substring(0, 2);
    }

    /**
     * Mod-36 check character over the first 14 positions.
     *
     * <p>Each character's value in the 36-character set is multiplied by an alternating
     * factor of 1 and 2; the quotient and remainder of that product against 36 are both
     * added to the running total. The check character is whatever brings the total to a
     * multiple of 36.
     */
    private static char checkDigit(String gstin) {
        int total = 0;
        for (int i = 0; i < 14; i++) {
            int value = CHARSET.indexOf(gstin.charAt(i));
            if (value < 0) return ' ';
            int product = value * (i % 2 == 0 ? 1 : 2);
            total += (product / 36) + (product % 36);
        }
        return CHARSET.charAt((36 - (total % 36)) % 36);
    }
}
