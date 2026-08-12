package com.hms.api.catalog;

import com.hms.api.shared.ApiResponse;
import com.hms.infrastructure.persistence.catalog.Icd10CodeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ICD-10 diagnosis lookup for the pre-auth form — Screen 4.1.
 *
 * <p>Returns an empty list until the official WHO / MoHFW release is loaded, and
 * that is the intended behaviour: an empty result is honest, whereas a partial
 * hand-written list looks authoritative while silently missing the diagnosis a
 * clinician needs.
 *
 * <p>Guarded by {@code PREAUTH_MANAGE} rather than left open. The catalogue is
 * public reference data, but an unauthenticated search endpoint over a hospital
 * domain is a free enumeration surface, and there is no reason to offer one.
 */
@RestController
@RequestMapping("/catalog/icd10")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('PREAUTH_MANAGE','')")
public class Icd10Controller {

    private static final int MAX_RESULTS = 20;

    private final Icd10CodeJpaRepository repository;

    /** Type-ahead over code prefix and title words. */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Icd10Result>>> search(@RequestParam String q) {
        String query = q == null ? "" : q.trim();
        // Two characters is where a prefix stops matching most of the catalogue.
        // Below that the result is noise and the query is expensive.
        if (query.length() < 2) {
            return ResponseEntity.ok(ApiResponse.of(List.of()));
        }

        List<Icd10Result> results = repository.search(query, PageRequest.of(0, MAX_RESULTS))
            .stream()
            .map(c -> new Icd10Result(c.getCode(), c.getTitle(), c.getChapter()))
            .toList();

        return ResponseEntity.ok(ApiResponse.of(results));
    }

    public record Icd10Result(String code, String title, String chapter) {
    }
}
