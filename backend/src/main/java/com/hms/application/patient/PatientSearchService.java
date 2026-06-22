package com.hms.application.patient;

import com.hms.api.patient.response.PatientResponse;
import com.hms.domain.patient.model.Patient;
import com.hms.infrastructure.mapper.PatientMapper;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import com.hms.security.encryption.PiiSearchTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Encrypted-aware patient search service.
 *
 * Because firstName / lastName / contactNumber are AES-256-GCM encrypted,
 * SQL LIKE on those columns no longer works. This service implements a
 * three-tier strategy:
 *
 * Tier 1 — Patient number prefix (SQL, fast, indexed):
 *   "P001234" → searchByPatientNumber() → exact result set
 *
 * Tier 2 — Phone number (SQL via HMAC token, indexed):
 *   "9876543210" → contactNumberToken lookup → exact result set
 *
 * Tier 3 — Name (application-layer, decrypt + filter):
 *   "Ravi" → load recent active patients page → decrypt firstName/lastName
 *            in Java → filter by contains(query, ignoreCase)
 *
 * The heuristic for which tier to apply:
 *   - All-digits (≥8 chars) → phone token lookup
 *   - Looks like a patient number (alpha + digits) → patient number search
 *   - Otherwise → name search (Tier 3)
 *
 * IMPORTANT: Tier 3 is bounded. We load at most MAX_NAME_SEARCH_LOAD patients
 * (default 500) from the DB and filter in memory. This is acceptable for
 * small-to-medium deployments (< 100k patients). For larger scale, consider
 * adding an external search index (OpenSearch / Elasticsearch) with encrypted
 * field indexing.
 */
@Service
@RequiredArgsConstructor
public class PatientSearchService {

    /** Maximum number of patients fetched from DB for in-memory name filtering. */
    private static final int MAX_NAME_SEARCH_LOAD = 500;

    private final PatientJpaRepository patientRepo;
    private final PatientMapper patientMapper;
    private final PiiSearchTokenService tokenService;
    private final NumberSequenceJpaRepository numberSequenceRepo;

    /**
     * Unified patient search entry point. Returns a page of matching PatientResponse.
     *
     * @param query    raw search input from the user
     * @param pageable pagination
     */
    @Transactional(readOnly = true)
    public Page<PatientResponse> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            // Empty query — return recent patients
            return patientRepo.findAllActive(pageable)
                    .map(p -> toResponse(p));
        }

        String trimmed = query.strip();

        // Tier 2: All-digit string ≥ 8 chars → phone token lookup
        if (trimmed.replaceAll("[^0-9]", "").length() >= 8) {
            return searchByPhone(trimmed, pageable);
        }

        // Tier 1: Looks like a patient number (contains digit after alpha prefix)
        if (looksLikePatientNumber(trimmed)) {
            return patientRepo.searchByPatientNumber(trimmed, pageable)
                    .map(p -> toResponse(p));
        }

        // Tier 3: Name search — decrypt in memory
        return searchByNameInMemory(trimmed, pageable);
    }

    // ── Tier 2: Phone token ────────────────────────────────────────────────────

    private Page<PatientResponse> searchByPhone(String rawPhone, Pageable pageable) {
        String token = tokenService.phoneToken(rawPhone);
        if (token == null) return Page.empty(pageable);

        List<Patient> matches = patientRepo.findByContactNumberToken(token);
        List<PatientResponse> responses = matches.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // Manual pagination over the (usually tiny) result set
        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), responses.size());
        List<PatientResponse> page = start >= responses.size()
                ? Collections.emptyList()
                : responses.subList(start, end);

        return new PageImpl<>(page, pageable, responses.size());
    }

    // ── Tier 3: In-memory name search ──────────────────────────────────────────

    private Page<PatientResponse> searchByNameInMemory(String query, Pageable pageable) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        // Load a bounded batch — decryption happens transparently via JPA converter
        Page<Patient> allActive = patientRepo.findAllActive(
                PageRequest.of(0, MAX_NAME_SEARCH_LOAD,
                        org.springframework.data.domain.Sort.by("createdAt").descending()));

        // Filter by decrypted name fields
        List<Patient> matched = allActive.getContent().stream()
                .filter(p -> nameMatches(p, lowerQuery))
                .collect(Collectors.toList());

        // Map + manually paginate
        List<PatientResponse> responses = matched.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), responses.size());
        List<PatientResponse> pageContent = start >= responses.size()
                ? Collections.emptyList()
                : responses.subList(start, end);

        return new PageImpl<>(pageContent, pageable, responses.size());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean nameMatches(Patient p, String lowerQuery) {
        String fn = p.getFirstName();
        String ln = p.getLastName();
        return (fn != null && fn.toLowerCase(Locale.ROOT).contains(lowerQuery))
            || (ln != null && ln.toLowerCase(Locale.ROOT).contains(lowerQuery));
    }

    private boolean looksLikePatientNumber(String s) {
        // e.g. "P-0012", "REG001", "UHID/001"
        return s.matches(".*[A-Za-z].*[0-9].*") || s.matches(".*[0-9].*[A-Za-z].*");
    }

    private PatientResponse toResponse(Patient p) {
        String patientNumber = numberSequenceRepo.findById(p.getId())
                .map(NumberSequenceEntity::getValue)
                .orElse("—");
        return patientMapper.toResponse(p, patientNumber);
    }
}
