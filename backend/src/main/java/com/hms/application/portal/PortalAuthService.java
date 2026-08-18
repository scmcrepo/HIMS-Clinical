package com.hms.application.portal;

import com.hms.infrastructure.persistence.portal.PortalSessionEntity;
import com.hms.infrastructure.persistence.portal.PortalSessionJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.security.portal.PortalTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a verified number into a patient-scoped session, and keeps that session
 * alive through rotating refresh tokens.
 *
 * <p>The security-critical decision here is what happens when a refresh token is
 * presented twice. A legitimate device never does that: after a successful
 * refresh it holds the successor and the old token is dead. So a second
 * presentation means the token was captured — from a backup, a rooted device, a
 * proxy — and by then the attacker may already hold a live access token that
 * simply rejecting this request would not touch. The whole chain is therefore
 * revoked, which logs both the attacker and the patient out and forces a fresh
 * OTP. That is a real cost to a patient whose app has a bug, which is exactly
 * why the mobile client collapses concurrent refreshes into one request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final PortalSessionJpaRepository sessionRepo;
    private final BranchJpaRepository branchRepo;
    private final PortalLookupService lookupService;
    private final PortalTokenService tokenService;
    private final PortalProperties properties;
    private final MeterRegistry meterRegistry;

    public record IssuedSession(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt) {}

    /**
     * Exchanges an identity token for patient-scoped tokens.
     *
     * <p>The chosen patient is re-derived from the OTP-verified number rather
     * than trusted from the request. This is the check that makes the two-token
     * split worth having: without it, a client holding any candidate list could
     * substitute a sibling's patient id and the server would have nothing to
     * compare it against.
     */
    @Transactional
    public IssuedSession establishSession(
            String contactNumberToken, UUID patientId, UUID tenantId, UUID branchId,
            String deviceLabel) {

        List<PortalLookupService.HospitalCandidate> candidates =
            lookupService.findCandidates(contactNumberToken);

        boolean patientBelongs = candidates.stream()
            .filter(h -> h.tenantId().equals(tenantId))
            .flatMap(h -> h.patients().stream())
            .anyMatch(p -> p.patientId().equals(patientId));

        if (!patientBelongs) {
            log.warn("event=portal.session.rejected reason=patient_not_in_candidate_set "
                + "patient_id={} tenant_id={}", patientId, tenantId);
            meterRegistry.counter("hms_portal_sessions_total", "outcome", "not_in_candidate_set")
                .increment();
            throw new PortalException(
                PortalErrorCode.PATIENT_NOT_IN_CANDIDATE_SET,
                "portal.session.patient_not_in_candidate_set");
        }

        // The branch must be active and belong to the chosen tenant. A branch
        // from another tenant would produce a principal whose tenant and branch
        // filters disagree — rows visible to one and not the other.
        Optional<BranchEntity> branch = branchRepo.findById(branchId);
        if (branch.isEmpty()
                || !tenantId.equals(branch.get().getTenantId())
                || branch.get().getStatus() != 1) {
            throw new PortalException(
                PortalErrorCode.VALIDATION_FAILED, "portal.session.invalid_branch");
        }

        enforceDeviceLimit(patientId);

        UUID chainId = UUID.randomUUID();
        return issue(patientId, tenantId, branchId, chainId, null, deviceLabel);
    }

    /**
     * Rotates a refresh token.
     *
     * <p>Not idempotent, and cannot be: the whole point is that the presented
     * token is spent by the call.
     */
    @Transactional
    public IssuedSession refresh(String presentedRefreshToken) {
        String hash = sha256(presentedRefreshToken);
        Instant now = Instant.now();

        Optional<PortalSessionEntity> found = sessionRepo.findByRefreshTokenHash(hash);
        if (found.isEmpty()) {
            meterRegistry.counter("hms_portal_refresh_total", "outcome", "unknown").increment();
            throw new PortalException(PortalErrorCode.UNAUTHORIZED, "portal.refresh.unknown_token");
        }

        PortalSessionEntity session = found.get();

        if (session.getConsumedAt() != null) {
            // Reuse. Revoke everything on this chain, not just this row: the
            // attacker's successor token is elsewhere on it.
            sessionRepo.revokeChain(
                session.getChainId(), now, PortalSessionEntity.REASON_REUSE_DETECTED);
            log.error("event=portal.session.refresh_reuse_detected patient_id={} chain_id={}",
                session.getPatientId(), session.getChainId());
            meterRegistry.counter("hms_portal_refresh_reuse_total").increment();
            throw new PortalException(PortalErrorCode.UNAUTHORIZED, "portal.refresh.reuse_detected");
        }

        if (session.getRevokedAt() != null) {
            meterRegistry.counter("hms_portal_refresh_total", "outcome", "revoked").increment();
            throw new PortalException(PortalErrorCode.UNAUTHORIZED, "portal.refresh.revoked");
        }

        if (!now.isBefore(session.getExpiresAt())) {
            meterRegistry.counter("hms_portal_refresh_total", "outcome", "expired").increment();
            throw new PortalException(PortalErrorCode.UNAUTHORIZED, "portal.refresh.expired");
        }

        session.setConsumedAt(now);
        sessionRepo.save(session);

        return issue(
            session.getPatientId(), session.getTenantId(), session.getBranchId(),
            session.getChainId(), session.getId(), session.getDeviceLabel());
    }

    @Transactional
    public void logout(UUID chainId) {
        if (chainId == null) return;
        int revoked = sessionRepo.revokeChain(
            chainId, Instant.now(), PortalSessionEntity.REASON_LOGOUT);
        log.info("event=portal.session.logged_out chain_id={} rows={}", chainId, revoked);
    }

    /**
     * Revokes every session for a patient. Called on consent withdrawal and by
     * the DPDP erasure job — a withdrawn consent that leaves a live token in a
     * pocket has not actually been withdrawn.
     */
    @Transactional
    public void revokeAllForPatient(UUID patientId, String reason) {
        Instant now = Instant.now();
        sessionRepo.findLiveByPatient(patientId, now)
            .forEach(s -> sessionRepo.revokeChain(s.getChainId(), now, reason));
        log.info("event=portal.session.revoked_all patient_id={} reason={}", patientId, reason);
    }

    /** Erasure: session rows tie a patient id to devices and timestamps. */
    @Transactional
    public int deleteAllForPatient(UUID patientId) {
        return sessionRepo.deleteAllForPatient(patientId);
    }

    @Transactional
    public int purgeExpired() {
        return sessionRepo.purgeExpiredBefore(
            Instant.now().minus(java.time.Duration.ofDays(30)));
    }

    // ── internals ───────────────────────────────────────────────────────────

    /**
     * Caps concurrent devices, revoking the oldest chain rather than refusing
     * the new login. Refusing would strand a patient whose old phone was lost or
     * wiped, with no way to recover except a call to the hospital.
     */
    private void enforceDeviceLimit(UUID patientId) {
        Instant now = Instant.now();
        List<PortalSessionEntity> live = sessionRepo.findLiveByPatient(patientId, now);

        List<UUID> distinctChains = live.stream()
            .map(PortalSessionEntity::getChainId)
            .distinct()
            .toList();

        int allowed = properties.getMaxActiveDevices();
        if (distinctChains.size() < allowed) return;

        // findLiveByPatient is newest-first, so the tail is the oldest.
        for (UUID chainId : distinctChains.subList(allowed - 1, distinctChains.size())) {
            sessionRepo.revokeChain(chainId, now, PortalSessionEntity.REASON_DEVICE_LIMIT);
            log.info("event=portal.session.device_limit_revoked patient_id={} chain_id={}",
                patientId, chainId);
        }
    }

    private IssuedSession issue(
            UUID patientId, UUID tenantId, UUID branchId, UUID chainId, UUID parentId,
            String deviceLabel) {

        Instant now = Instant.now();
        String refreshToken = newRefreshToken();

        PortalSessionEntity session = new PortalSessionEntity();
        session.setChainId(chainId);
        session.setParentId(parentId);
        session.setPatientId(patientId);
        session.setTenantId(tenantId);
        session.setBranchId(branchId);
        session.setRefreshTokenHash(sha256(refreshToken));
        session.setDeviceLabel(truncate(deviceLabel));
        session.setIssuedAt(now);
        session.setExpiresAt(now.plus(properties.getRefreshTokenTtl()));
        sessionRepo.save(session);

        String accessToken = tokenService.issueAccessToken(patientId, tenantId, branchId, chainId);

        log.info("event=portal.session.issued patient_id={} tenant_id={} branch_id={} chain_id={}",
            patientId, tenantId, branchId, chainId);
        meterRegistry.counter("hms_portal_sessions_total", "outcome", "issued").increment();

        return new IssuedSession(
            accessToken,
            refreshToken,
            now.plus(properties.getAccessTokenTtl()),
            session.getExpiresAt());
    }

    /**
     * 256 bits from {@link SecureRandom}, URL-safe base64.
     *
     * <p>Opaque rather than a JWT on purpose: a refresh token has to be
     * revocable, and a self-contained signed token is valid until it expires no
     * matter what the database says. Reuse detection needs a row to check
     * against.
     */
    private static String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, not BCrypt — unlike a 6-digit OTP, a 256-bit random token has no
     * brute-forceable structure, and this runs on every refresh where an
     * adaptive hash would be pure latency.
     */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String truncate(String label) {
        if (label == null) return null;
        String cleaned = label.strip();
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 120);
    }
}
