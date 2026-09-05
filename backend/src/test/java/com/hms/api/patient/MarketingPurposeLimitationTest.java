package com.hms.api.patient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WO-031 / card B-002 — no MARKETING-permissioned endpoint returns patient
 * personal data.
 *
 * <h2>The defect this exists to prevent recurring</h2>
 * {@code GET /patient/getPatientsForMaketing} returned up to 1000 full
 * {@code PatientResponse} records — first name, last name, date of birth,
 * contact number, email, blood group, address — every one an
 * {@code EncryptedStringConverter} column. It was a bulk decryption of the
 * patient base, permissioned {@code hasPermission('MARKETING','')}, passing
 * through no {@code ConsentGate}. {@code GET /patient/getPatientBySearch} was
 * the same defect in CSV form.
 *
 * <p>DPDP s. 6 requires consent specific to the purpose. Consent to treatment is
 * not consent to marketing, and MARKETING was already a declared
 * {@code ConsentPurpose} with {@code requiredForCare = false}, seeded by V205
 * with draft notice text in V211. The machinery was there; the endpoint never
 * consulted it. Both were deleted in WO-031.
 *
 * <p>Deletion is not a control. Nothing stops the next person rebuilding either
 * one, and the shape of the mistake is attractive: a marketing team asks for a
 * patient list, a MARKETING permission already exists, and returning
 * {@code PatientResponse} is the path of least resistance. This test is the
 * control.
 *
 * <h2>Why source-level, and what that does not catch</h2>
 * A behavioural test would need every controller wired up and a decision about
 * what "returns PII" means at runtime. The property here is structural — which
 * permission guards which return type — and it is visible in the source.
 *
 * <p>The limits are worth stating. This reads the annotation and signature of
 * each MARKETING-guarded method, not its body, so a method returning
 * {@code Map<String,Object>} or a freshly-written DTO assembled from patient
 * fields would pass. {@link #piiResponseTypes()} is derived from field names
 * rather than fixed, so new PII types are covered automatically, but a type that
 * spells its fields differently would not be. It catches the mistake that was
 * actually made and the obvious ways back to it, which is what a regression test
 * is for — not every conceivable route.
 */
@DisplayName("B-002: MARKETING permission never reaches patient PII")
class MarketingPurposeLimitationTest {

    private static final Path API = Paths.get("src/main/java/com/hms/api");

    /**
     * Field names that mean decrypted patient personal data.
     *
     * <p>Three or more of these in one record is the signal. One alone is not:
     * plenty of legitimate DTOs carry an {@code email} without being a patient
     * dump, and a threshold of one would make this test fire on everything and
     * therefore be deleted.
     */
    private static final Set<String> PII_FIELDS = Set.of(
        "firstName", "lastName", "fullName", "contactNumber",
        "email", "address", "dateOfBirth", "bloodGroup");

    private static final Pattern RECORD =
        Pattern.compile("record\\s+(\\w+)\\s*\\((.*?)\\)\\s*\\{", Pattern.DOTALL);

    /** A {@code @PreAuthorize} naming MARKETING, plus whatever follows it. */
    private static final Pattern MARKETING_GUARD =
        Pattern.compile("@PreAuthorize\\s*\\([^)]*MARKETING[^)]*\\)");

    private List<Path> apiSources() throws IOException {
        try (var paths = Files.walk(API)) {
            return paths.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /**
     * Response types carrying enough patient fields to count as a patient record.
     *
     * <p>Computed rather than listed so that a new DTO with the same shape is
     * covered the day it is written. A hardcoded list would have to be kept in
     * step by the same person who is adding the endpoint this test is meant to
     * stop.
     */
    private Set<String> piiResponseTypes() throws IOException {
        Set<String> types = new LinkedHashSet<>();
        for (Path p : apiSources()) {
            Matcher m = RECORD.matcher(Files.readString(p));
            while (m.find()) {
                String components = m.group(2);
                long found = PII_FIELDS.stream()
                    .filter(f -> Pattern.compile("\\b" + f + "\\b").matcher(components).find())
                    .count();
                if (found >= 3) {
                    types.add(m.group(1));
                }
            }
        }
        return types;
    }

    @Test
    @DisplayName("the PII-type detector actually finds the type that leaked")
    void detectorIsNotVacuous() throws IOException {
        // Guards the guard. If this detector silently stopped matching, every
        // assertion below would pass for the wrong reason — which is the failure
        // mode of every test built on a computed set.
        assertThat(piiResponseTypes())
            .as("PatientResponse is the exact type the removed endpoint returned")
            .contains("PatientResponse");
    }

    @Test
    @DisplayName("no MARKETING-guarded method returns a patient record type")
    void marketingEndpointsDoNotReturnPatientRecords() throws IOException {
        Set<String> piiTypes = piiResponseTypes();
        List<String> violations = new ArrayList<>();

        for (Path p : apiSources()) {
            String src = Files.readString(p);
            Matcher guard = MARKETING_GUARD.matcher(src);

            while (guard.find()) {
                // From the annotation to the opening brace of the method body:
                // the remaining annotations, the return type and the parameters.
                int from = guard.end();
                int brace = src.indexOf('{', from);
                if (brace < 0) {
                    continue;
                }
                String signature = src.substring(from, brace);

                // The consent check must be in THIS method, not merely somewhere
                // in the file. The first version of this test asked whether the
                // source contained "ConsentGate" at all, and PatientController
                // does — for an unrelated endpoint. It therefore passed cleanly
                // with the removed endpoint pasted straight back in, which is the
                // whole defect it exists to catch. A file-level check standing in
                // for a per-method property is worse than no check, because it
                // reads as coverage.
                String method = signature + bodyOf(src, brace);
                boolean gated = method.contains("ConsentGate")
                             || method.contains("ConsentPurpose.MARKETING");

                for (String type : piiTypes) {
                    if (!Pattern.compile("\\b" + type + "\\b").matcher(signature).find()) {
                        continue;
                    }
                    // A MARKETING endpoint is not forbidden — it is forbidden
                    // WITHOUT a per-patient consent check. That is the condition
                    // WO-031 set for rebuilding either endpoint, so the test
                    // enforces the condition rather than a blanket ban.
                    if (!gated) {
                        violations.add(p.getFileName() + " returns " + type
                                       + " under a MARKETING permission with no ConsentGate");
                    }
                }
            }
        }

        assertThat(violations)
            .as("DPDP s. 6 requires consent specific to the purpose, and consent "
                + "to treatment is not consent to marketing. If this endpoint is "
                + "genuinely needed, rebuild it behind ConsentGate with a "
                + "per-patient MARKETING check, working filters and a page cap — "
                + "see the rationale block in PatientController.")
            .isEmpty();
    }

    @Test
    @DisplayName("the two removed endpoints stay removed")
    void removedEndpointsAreNotReinstated() throws IOException {
        String controller = Files.readString(
            Paths.get("src/main/java/com/hms/api/patient/PatientController.java"));

        // Note the misspelling: the original mapping really was
        // getPatientsForMaketing. Matching the correct spelling would miss a
        // straight revert of the commit that removed it.
        assertThat(controller)
            .as("reverting the WO-031 commit restores a bulk decryption of the "
                + "patient base; the rationale block says explicitly not to")
            .doesNotContain("@GetMapping(\"/getPatientsForMaketing\")")
            .doesNotContain("@GetMapping(\"/getPatientBySearch\")");
    }

    @Test
    @DisplayName("MARKETING is not quietly attached to any other patient endpoint")
    void marketingGuardsNothingInThePatientApi() throws IOException {
        // Today there are no MARKETING-guarded endpoints at all. That is a
        // stronger position than "none that return PII", and it is worth
        // detecting the moment it changes — not to forbid it, but so that adding
        // one is a decision someone makes deliberately, with this test's failure
        // message in front of them.
        List<String> guarded = new ArrayList<>();
        for (Path p : apiSources()) {
            if (MARKETING_GUARD.matcher(Files.readString(p)).find()) {
                guarded.add(p.getFileName().toString());
            }
        }

        assertThat(guarded)
            .as("A MARKETING-permissioned endpoint has appeared. That may be "
                + "legitimate, but it needs a per-patient ConsentGate check, "
                + "working filters and a page cap before it ships. Add the file "
                + "here once those are in place.")
            .isEmpty();
    }

    /**
     * The method body starting at {@code openBrace}, by brace matching.
     *
     * <p>Crude, and adequate: it can be fooled by a brace inside a string
     * literal or a comment, which would end the body early and could only ever
     * make this test stricter, never blinder. Returns the rest of the file if
     * the braces do not balance, which again fails safe.
     */
    private String bodyOf(String src, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(openBrace, i + 1);
                }
            }
        }
        return src.substring(openBrace);
    }
}
