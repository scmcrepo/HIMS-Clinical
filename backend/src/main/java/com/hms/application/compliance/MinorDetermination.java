package com.hms.application.compliance;

import com.hms.infrastructure.persistence.patient.PatientDobView;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;

/**
 * Whether a patient is a child, decided from their record rather than from a
 * checkbox — WO-032 / F3.
 *
 * <h2>Why this exists</h2>
 * DPDP s. 9 requires verifiable parental consent before processing a child's
 * personal data. {@code ConsentService.grant} enforced that correctly:
 * {@code minor && !guardianVerified} throws.
 *
 * <p>But {@code minor} arrived as a boolean on the request body, defaulting to
 * {@code false}, and nothing anywhere compared it to the patient's date of
 * birth. So the s. 9 control was only ever as strong as whoever ticked the box
 * on a busy front desk — and it failed in the unsafe direction, because the
 * default is "adult". A paediatric patient whose form was left unticked got a
 * consent record that looks entirely clean in an audit.
 *
 * <p>That is the same shape as the self-granting consent defect WO-022 fixed: a
 * record asserting something no one actually checked. The fix is the same in
 * spirit — derive the fact, and refuse to record a claim that contradicts it.
 *
 * <h2>Eighteen</h2>
 * The Act defines a child as someone who has not completed eighteen years.
 * Eighteen is therefore a statutory boundary and not a tunable, which is why it
 * is a constant here rather than a property. If the age ever changes it changes
 * by amendment, and a code change is the appropriate amount of friction.
 *
 * <h2>Unknown is not adult</h2>
 * {@link #isMinor} returns an empty Optional when the patient has neither a real
 * nor an estimated date of birth, and callers must treat that as unknown rather
 * than as an adult. Collapsing unknown into false is exactly how the original
 * defect behaved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinorDetermination {

    /** DPDP s. 2(f): a child is an individual who has not completed eighteen years. */
    public static final int MAJORITY_AGE = 18;

    private final PatientJpaRepository patients;

    /**
     * @return {@code true}/{@code false} where a date of birth is on file,
     *         empty where the patient has neither a recorded nor an estimated
     *         one, or is not visible in the current tenant
     */
    @Transactional(readOnly = true)
    public Optional<Boolean> isMinor(UUID patientId) {
        if (patientId == null) {
            return Optional.empty();
        }

        return patients.findDobById(patientId)
                       .map(this::effectiveDob)
                       .filter(dob -> dob != null)
                       .map(dob -> Period.between(dob, LocalDate.now()).getYears() < MAJORITY_AGE);
    }

    /**
     * The recorded date of birth, or the estimate reception took when the
     * patient did not know it.
     *
     * <p>Using the estimate matters more than it looks. A large share of
     * paediatric registrations in this deployment carry only an estimate, and
     * ignoring it would leave precisely the population s. 9 protects in the
     * "undetermined" bucket.
     *
     * <p>A future-dated birth date is treated as no date at all rather than as a
     * negative age. It is a data-entry error, and guessing at its intent would
     * be worse than declining to answer.
     */
    private LocalDate effectiveDob(PatientDobView view) {
        LocalDate dob = view.getDateOfBirth() != null
                      ? view.getDateOfBirth()
                      : view.getEstimatedDateOfBirth();
        if (dob == null || dob.isAfter(LocalDate.now())) {
            return null;
        }
        return dob;
    }
}
