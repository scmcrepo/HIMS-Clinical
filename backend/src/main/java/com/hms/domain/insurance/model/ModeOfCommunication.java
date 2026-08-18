package com.hms.domain.insurance.model;

/**
 * How a pre-auth or enhancement was transmitted to the TPA, or how the TPA's
 * decision came back (WO-020, Stages 1–4).
 *
 * <p>Replaces the free-text {@code communication} column for the desk flow. The
 * mode determines which endpoint field is mandatory: {@link #FAX} requires a fax
 * number, {@link #MAIL} requires a mail id. Without that pairing the desk can
 * record "faxed" with nowhere to say where, which is the state the current
 * single unstructured column leaves it in.
 */
public enum ModeOfCommunication {
    FAX,
    MAIL
}
