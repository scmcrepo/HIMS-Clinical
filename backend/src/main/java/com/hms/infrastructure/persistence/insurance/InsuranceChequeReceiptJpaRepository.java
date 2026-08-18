package com.hms.infrastructure.persistence.insurance;

import com.hms.domain.insurance.model.InsuranceChequeReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Cheque receipts for a claim (WO-020, Stage 7).
 *
 * <p>Every query here is tenant-scoped by the Hibernate {@code tenantFilter} on
 * {@link InsuranceChequeReceipt}, activated per request by
 * {@code TenantResolutionFilter}. There is deliberately no findAll-style method:
 * cheques are only ever read in the context of one claim.
 */
public interface InsuranceChequeReceiptJpaRepository
        extends JpaRepository<InsuranceChequeReceipt, UUID> {

    List<InsuranceChequeReceipt> findByInsuranceIdOrderByChequeDateDesc(UUID insuranceId);

    void deleteByInsuranceId(UUID insuranceId);
}
