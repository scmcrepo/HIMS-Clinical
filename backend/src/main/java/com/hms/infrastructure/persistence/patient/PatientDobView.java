package com.hms.infrastructure.persistence.patient;

import java.time.LocalDate;

/**
 * The two date-of-birth columns, without decrypting the rest of the patient.
 *
 * <p>Exists for {@code MinorDetermination} (WO-032 / F3), which needs to answer
 * "is this patient a child" at consent capture. Reading the full entity to get
 * there would decrypt name, contact number, email and address for no reason.
 *
 * <p>Both fields are nullable. {@code dateOfBirth} is the real one;
 * {@code estimatedDateOfBirth} is what reception records when a patient does not
 * know their birth date, which is common enough that ignoring it would leave a
 * large group of paediatric patients undetermined.
 */
public interface PatientDobView {

    LocalDate getDateOfBirth();

    LocalDate getEstimatedDateOfBirth();
}
