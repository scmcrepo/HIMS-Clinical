package com.hms.security.encryption;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents that a field contains Personally Identifiable Information (PII)
 * and is encrypted at rest using {@link EncryptedStringConverter}.
 *
 * This annotation is informational — the actual encryption is done by
 * {@code @Convert(converter = EncryptedStringConverter.class)} on the same field.
 *
 * Place both together:
 * <pre>
 * {@literal @}PiiField(category = PiiCategory.CONTACT, description = "Patient mobile number")
 * {@literal @}Convert(converter = EncryptedStringConverter.class)
 * {@literal @}Column(name = "contact_number", length = 512)
 * private String contactNumber;
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PiiField {

    /** The PII category for audit and compliance reporting. */
    PiiCategory category() default PiiCategory.OTHER;

    /** Human-readable description of what this field stores. */
    String description() default "";

    /** Regulation under which this field is classified as PII. */
    String[] regulations() default {"DPDPA", "HIPAA"};

    enum PiiCategory {
        /** Full name, first name, last name, salutation */
        NAME,
        /** Phone, mobile, contact number */
        CONTACT,
        /** Email address */
        EMAIL,
        /** Physical or mailing address */
        ADDRESS,
        /** Date of birth, age */
        DATE_OF_BIRTH,
        /** Government-issued ID numbers */
        NATIONAL_ID,
        /** Insurance policy numbers, pre-auth numbers */
        INSURANCE_ID,
        /** Financial account identifiers */
        FINANCIAL_ID,
        /** GST/tax registration numbers */
        TAX_ID,
        /** Biometric or health identifiers (blood group) */
        HEALTH_IDENTIFIER,
        /** Medical diagnosis text */
        CLINICAL,
        /** Doctor/consultant professional identifiers */
        PROFESSIONAL_ID,
        OTHER
    }
}
