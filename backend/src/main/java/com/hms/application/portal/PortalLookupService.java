package com.hms.application.portal;

import com.hms.domain.patient.model.Patient;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.persistence.portal.PortalPatientLookupRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Answers "which hospitals hold a record for this verified mobile number, and
 * which patients at each?"
 *
 * <p>Two passes, and the split is the whole security design:
 *
 * <ol>
 *   <li>{@link PortalPatientLookupRepository} returns <em>ids only</em>, across
 *       all tenants, unfiltered. No personal data is read and nothing is
 *       decrypted.</li>
 *   <li>For each distinct tenant, {@link PortalTenantScope} enters that tenant's
 *       scope and the display fields are read under the normal Hibernate tenant
 *       filter — so the decrypting read is as scoped as any staff query.</li>
 * </ol>
 *
 * <p>Doing it in one unfiltered query would be shorter and would also mean a
 * single query returning decrypted names from four hospitals at once, with the
 * only thing standing between that result set and a leak being the correctness
 * of the {@code WHERE} clause.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalLookupService {

    private final PortalPatientLookupRepository lookupRepo;
    private final PatientJpaRepository patientRepo;
    private final TenantJpaRepository tenantRepo;
    private final BranchJpaRepository branchRepo;
    private final PortalTenantScope tenantScope;
    private final MeterRegistry meterRegistry;

    public record PatientCandidate(
        UUID patientId,
        String fullName,
        Integer age,
        String gender,
        String numberSequenceSuffix,
        String photoUrl) {}

    public record BranchSummary(
        UUID branchId,
        String name,
        String code,
        String address,
        String contactNumber,
        boolean isDefault,
        boolean isActive) {}

    public record HospitalCandidate(
        UUID tenantId,
        String tenantName,
        String address,
        String contactNumber,
        String logoUrl,
        List<PatientCandidate> patients,
        List<BranchSummary> branches) {}

    /**
     * @param contactNumberToken the HMAC token returned by a verified OTP —
     *                           never a raw mobile number, so this method cannot
     *                           be called with an unverified one by accident
     */
    @Transactional(readOnly = true)
    public List<HospitalCandidate> findCandidates(String contactNumberToken) {
        Timer.Sample sample = Timer.start(meterRegistry);

        List<PortalPatientLookupRepository.PortalPatientIdProjection> ids =
            lookupRepo.findIdsByContactNumberTokenAcrossTenants(contactNumberToken);

        // Grouped by tenant so each tenant's scope is entered exactly once,
        // rather than once per patient.
        Map<UUID, List<UUID>> byTenant = new LinkedHashMap<>();
        for (var row : ids) {
            byTenant.computeIfAbsent(row.getTenantId(), k -> new ArrayList<>())
                .add(row.getPatientId());
        }

        List<HospitalCandidate> candidates = new ArrayList<>();
        for (Map.Entry<UUID, List<UUID>> entry : byTenant.entrySet()) {
            enrich(entry.getKey(), entry.getValue()).ifPresent(candidates::add);
        }

        // Recorded with a `found` label so the two latency distributions can be
        // compared. A consistent gap between them is a timing oracle: it tells a
        // prober whether a number is registered without them ever passing the
        // OTP. WO-017 §6 alerts on >150ms divergence at p95.
        sample.stop(Timer.builder("hms_portal_lookup_seconds")
            .tag("found", String.valueOf(!candidates.isEmpty()))
            .register(meterRegistry));

        log.info("event=portal.lookup.completed tenant_count={} patient_count={}",
            candidates.size(), ids.size());

        return candidates;
    }

    /**
     * Reads one tenant's display data inside that tenant's scope.
     *
     * <p>Returns empty when the tenant is inactive: a hospital that has left the
     * platform must not appear in a patient's list, even though their old rows
     * are still there.
     */
    private Optional<HospitalCandidate> enrich(UUID tenantId, List<UUID> patientIds) {
        Optional<TenantEntity> tenant = tenantRepo.findById(tenantId);
        if (tenant.isEmpty() || tenant.get().getStatus() != EntityStatus.ACTIVE.getOrdinalValue()) {
            log.debug("event=portal.lookup.tenant_skipped tenant_id={} reason=inactive", tenantId);
            return Optional.empty();
        }

        return tenantScope.call(tenantId, null, () -> {
            List<PatientCandidate> patients = new ArrayList<>();
            for (UUID patientId : patientIds) {
                patientRepo.findById(patientId)
                    .filter(p -> p.getStatus() == EntityStatus.ACTIVE)
                    .map(this::toCandidate)
                    .ifPresent(patients::add);
            }

            if (patients.isEmpty()) {
                return Optional.<HospitalCandidate>empty();
            }

            List<BranchSummary> branches = branchRepo
                .findAllByTenantIdAndStatus(tenantId, (short) EntityStatus.ACTIVE.getOrdinalValue())
                .stream()
                .map(this::toBranchSummary)
                .toList();

            TenantEntity t = tenant.get();
            return Optional.of(new HospitalCandidate(
                t.getId(), t.getName(), t.getAddress(), t.getContactNumber(),
                null, patients, branches));
        });
    }

    /**
     * Composes the display name server-side.
     *
     * <p>The client is given {@code fullName} and {@code age} rather than the
     * parts, so no salutation rules or date-of-birth arithmetic ship to a device
     * — and, more to the point, so a date of birth the patient did not ask to
     * see never leaves the server.
     */
    private PatientCandidate toCandidate(Patient p) {
        StringBuilder name = new StringBuilder();
        if (p.getSalutation() != null && !p.getSalutation().isBlank()) {
            name.append(p.getSalutation().trim()).append(' ');
        }
        if (p.getFirstName() != null) {
            name.append(p.getFirstName().trim());
        }
        if (p.getLastName() != null && !p.getLastName().isBlank()) {
            name.append(' ').append(p.getLastName().trim());
        }

        return new PatientCandidate(
            p.getId(),
            name.toString().trim(),
            ageOf(p),
            p.getGender() != null ? p.getGender().name() : null,
            null,   // patient number is resolved by the caller that needs it
            null);
    }

    private BranchSummary toBranchSummary(BranchEntity b) {
        return new BranchSummary(
            b.getId(), b.getName(), b.getCode(), b.getAddress(), b.getContactNumber(),
            b.isDefault(), b.getStatus() == EntityStatus.ACTIVE.getOrdinalValue());
    }

    /**
     * Prefers the recorded date of birth, falling back to the estimate.
     *
     * <p>Many patients at an Indian hospital are registered with an estimated
     * age rather than an exact date; treating the estimate as absent would show
     * a blank age for a large share of real records.
     */
    private static Integer ageOf(Patient p) {
        LocalDate dob = p.getDateOfBirth() != null
            ? p.getDateOfBirth()
            : p.getEstimatedDateOfBirth();
        if (dob == null) return null;
        return Period.between(dob, LocalDate.now()).getYears();
    }
}
