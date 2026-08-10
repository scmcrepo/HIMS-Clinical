package com.hms.infrastructure.persistence.abha;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ABHA linkage lookups.
 *
 * <p>Every method here is reached through the Hibernate {@code tenantFilter},
 * so results are already narrowed to the caller's tenant. The one exception is
 * {@link #findByTransactionId(String)} — see its note.
 *
 * <p>There is deliberately no {@code findByAbhaNumber(String)}. The column is
 * encrypted with a non-deterministic cipher, so an equality match on it would
 * silently return nothing rather than fail loudly. Search the blind-index token
 * instead.
 */
public interface AbhaLinkageJpaRepository extends JpaRepository<AbhaLinkageEntity, UUID> {

    List<AbhaLinkageEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    Optional<AbhaLinkageEntity> findByPatientIdAndLinkageState(UUID patientId, String linkageState);

    /** Blind-index search. Pass a token from {@code PiiSearchTokenService}, never a raw ABHA number. */
    List<AbhaLinkageEntity> findByAbhaNumberToken(String abhaNumberToken);

    List<AbhaLinkageEntity> findByAbhaAddressToken(String abhaAddressToken);

    /**
     * Resume an in-flight enrolment by its ABDM transaction id.
     *
     * <p>The transaction id is minted by ABDM, is opaque, and is single-use, so
     * it identifies one enrolment attempt unambiguously. The tenant filter still
     * applies on this path because the caller is an authenticated staff session
     * — this is not a webhook.
     */
    Optional<AbhaLinkageEntity> findByTransactionId(String transactionId);
}
