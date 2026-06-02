package com.hms.api.referral;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.patient.model.Referral;
import com.hms.infrastructure.persistence.referral.ReferralJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/referrals") @RequiredArgsConstructor
public class ReferralController {
    private final ReferralJpaRepository repo;
    @GetMapping
    public ResponseEntity<ApiResponse<List<Referral>>> getAll() { return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive())); }
    @PostMapping
    public ResponseEntity<ApiResponse<Referral>> create(@RequestBody Referral req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Referral saved successfully", repo.save(req)));
    }
}
