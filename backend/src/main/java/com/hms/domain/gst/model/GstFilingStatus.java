package com.hms.domain.gst.model;

/**
 * Where a filing attempt has got to.
 *
 * <p>GENERATED is deliberately distinct from SUBMITTED: this module builds and stores a
 * payload whether or not anything is ever sent, and a hospital filing manually through
 * the GST portal will never move a record past GENERATED. Treating "we produced the
 * return" and "the GSP accepted it" as the same state would misreport that case.
 */
public enum GstFilingStatus {
    DRAFT,
    GENERATED,
    SUBMITTED,
    FILED,
    ERROR
}
