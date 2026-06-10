package com.hms.api.patient.request;

import com.hms.domain.patient.model.Gender;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testRegisterPatientRequest_Valid() {
        RegisterPatientRequest request = new RegisterPatientRequest(
                "Mr",
                "John",
                "Doe",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(1990, 1, 1),
                "9876543210",
                "john.doe@example.com",
                "A+",
                "123 Main St",
                null,
                null,
                null,
                false,
                false
        );

        Set<ConstraintViolation<RegisterPatientRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testRegisterPatientRequest_InvalidNamesAndBloodGroup() {
        RegisterPatientRequest request = new RegisterPatientRequest(
                "Mr",
                "John123", // invalid first name (contains digits)
                "Doe!",    // invalid last name (contains symbols)
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(1990, 1, 1),
                "9876543210",
                "john.doe@example.com",
                "OPositiveLongValue", // invalid blood group (> 10 chars)
                "123 Main St",
                null,
                null,
                null,
                false,
                false
        );

        Set<ConstraintViolation<RegisterPatientRequest>> violations = validator.validate(request);
        assertEquals(3, violations.size());

        boolean hasFirstNameViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("firstName") && v.getMessage().contains("First name must contain only alphabets"));
        boolean hasLastNameViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("lastName") && v.getMessage().contains("Last name must contain only alphabets"));
        boolean hasBloodGroupViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("bloodGroup") && v.getMessage().contains("Blood group must be at most 10 characters"));

        assertTrue(hasFirstNameViolation);
        assertTrue(hasLastNameViolation);
        assertTrue(hasBloodGroupViolation);
    }

    @Test
    void testUpdatePatientRequest_Valid() {
        UpdatePatientRequest request = new UpdatePatientRequest(
                "Mr",
                "Jane",
                "Smith",
                Gender.FEMALE,
                LocalDate.of(1995, 5, 5),
                LocalDate.of(1995, 5, 5),
                "9876543210",
                "jane.smith@example.com",
                "O-",
                "456 Oak Rd",
                null,
                null,
                false
        );

        Set<ConstraintViolation<UpdatePatientRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testUpdatePatientRequest_InvalidNamesAndBloodGroup() {
        UpdatePatientRequest request = new UpdatePatientRequest(
                "Mr",
                "Jane123", // invalid first name
                "Smith!",   // invalid last name
                Gender.FEMALE,
                LocalDate.of(1995, 5, 5),
                LocalDate.of(1995, 5, 5),
                "9876543210",
                "jane.smith@example.com",
                "ABPositiveVeryLong", // invalid blood group (> 10 chars)
                "456 Oak Rd",
                null,
                null,
                false
        );

        Set<ConstraintViolation<UpdatePatientRequest>> violations = validator.validate(request);
        assertEquals(3, violations.size());

        boolean hasFirstNameViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("firstName") && v.getMessage().contains("First name must contain only alphabets"));
        boolean hasLastNameViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("lastName") && v.getMessage().contains("Last name must contain only alphabets"));
        boolean hasBloodGroupViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("bloodGroup") && v.getMessage().contains("Blood group must be at most 10 characters"));

        assertTrue(hasFirstNameViolation);
        assertTrue(hasLastNameViolation);
        assertTrue(hasBloodGroupViolation);
    }
}
