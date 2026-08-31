package com.hms.application.compliance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level guard against the WO-022 defect being reintroduced.
 *
 * <p>The original bug was not a logic error anyone would spot in review — it read
 * as careful, defensive code:
 *
 * <pre>
 *   if (!consent.hasConsent(patientId, PURPOSE)) { consent.grant(...); }
 *   consent.requireConsent(patientId, PURPOSE);
 * </pre>
 *
 * <p>A unit test cannot catch its return, because a service that grants its own
 * consent passes every behavioural test you would think to write: consent is
 * present, the action proceeds, the metric increments. The only reliable signal
 * is the shape of the source, so that is what this asserts.
 *
 * <p>Deliberately a plain file scan rather than a bytecode or AST tool: it needs
 * no dependency, and a developer who trips it can see exactly which line is the
 * problem.
 */
class ConsentSelfGrantConventionTest {

    private static final Path APPLICATION = Paths.get("src/main/java/com/hms/application");

    /** {@code if (!...hasConsent(...))} — the branch that defeats the gate. */
    private static final Pattern NEGATED_HAS_CONSENT =
        Pattern.compile("if\\s*\\(\\s*!\\s*[\\w.]*hasConsent\\s*\\(");

    /** Any direct {@code .grant(} outside the compliance package. */
    private static final Pattern DIRECT_GRANT =
        Pattern.compile("\\.grant\\s*\\(");

    private List<Path> applicationSources() throws IOException {
        if (!Files.exists(APPLICATION)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(APPLICATION)) {
            return paths.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.toString().contains("/compliance/"))
                        .toList();
        }
    }

    @Test
    @DisplayName("No service branches on !hasConsent — that is the shape that made the gate unfailable")
    void noNegatedHasConsentBranch() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : applicationSources()) {
            String body = Files.readString(file);
            if (NEGATED_HAS_CONSENT.matcher(body).find()) {
                offenders.add(file.toString());
            }
        }
        assertThat(offenders)
            .as("Use ConsentGate.ensure(...). A service that decides for itself "
                + "what to do when consent is missing is one refactor away from "
                + "granting it. See WO-022.")
            .isEmpty();
    }

    @Test
    @DisplayName("No service outside application/compliance calls ConsentService.grant directly")
    void noDirectGrantOutsideCompliancePackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : applicationSources()) {
            String body = Files.readString(file);
            if (!body.contains("ConsentPurpose")) {
                continue;   // not consent-related; .grant( here means something else
            }
            if (DIRECT_GRANT.matcher(body).find()) {
                offenders.add(file.toString());
            }
        }
        assertThat(offenders)
            .as("Consent is captured through ConsentGate, which attributes it to "
                + "the authenticated user. A direct grant() call can pass a null "
                + "capturer, which is exactly how the fabricated rows were created.")
            .isEmpty();
    }

    @Test
    @DisplayName("Every ConsentPurpose has seeded notice text in V205")
    void everyPurposeHasSeededNotice() throws IOException {
        Path migration = Paths.get(
            "src/main/resources/db/migration/V205__consent_provenance_and_notices.sql");
        assertThat(Files.exists(migration))
            .as("V205 seeds the notice registry; without it the desk hard-blocks")
            .isTrue();

        String sql = Files.readString(migration);
        for (ConsentPurpose purpose : ConsentPurpose.values()) {
            assertThat(sql)
                .as("V205 must seed notice text for %s, or capturing that consent "
                    + "throws at runtime", purpose)
                .contains("'" + purpose.name() + "'");
        }
    }
}
