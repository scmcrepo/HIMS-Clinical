package com.hms.domain.catalog.model;

/**
 * How a service is treated for GST.
 *
 * <p>The distinction between these is not cosmetic — GST returns report them under
 * separate headings, and a supply reported in the wrong one is a misfiled return even
 * when the tax collected is zero in both cases:
 *
 * <ul>
 *   <li>{@link #EXEMPT} — supply is exempt by notification. Most healthcare services
 *       provided by a clinical establishment fall here.</li>
 *   <li>{@link #NIL_RATED} — taxable supply at a 0% rate. Not the same as exempt.</li>
 *   <li>{@link #NON_GST} — outside GST's scope entirely.</li>
 *   <li>{@link #TAXABLE} — attracts tax at {@code taxRate}.</li>
 * </ul>
 *
 * <p>{@link #UNCLASSIFIED} is the default for every service that existed before GST
 * classification was added. It is deliberately not a synonym for exempt: it means
 * nobody has ruled on this service yet, and the OP/IP report keeps it visible rather
 * than quietly reporting it as one thing or the other.
 */
public enum GstTreatment {

    UNCLASSIFIED,
    TAXABLE,
    EXEMPT,
    NIL_RATED,
    NON_GST;

    /** True when this service should carry tax at its configured rate. */
    public boolean isTaxable() {
        return this == TAXABLE;
    }

    /** True when the hospital has actually ruled on this service. */
    public boolean isClassified() {
        return this != UNCLASSIFIED;
    }

    public static GstTreatment fromString(String value) {
        if (value == null || value.isBlank()) return UNCLASSIFIED;
        for (GstTreatment t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) return t;
        }
        return UNCLASSIFIED;
    }
}
