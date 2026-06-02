package com.hms.domain.insurance.model;
/** String values stored in DB — matches legacy InsuranceStatus. */
public enum InsuranceStatus {
    ACTIVE,
    PRE_AUTH_REQUESTED,
    PRE_AUTH_RECEIVED,
    SETTLED,
    REJECTED
}
