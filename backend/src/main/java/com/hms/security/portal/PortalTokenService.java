package com.hms.security.portal;

import com.hms.application.portal.PortalErrorCode;
import com.hms.application.portal.PortalException;
import com.hms.application.portal.PortalProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Mints and validates the two portal token types.
 *
 * <p>Uses {@code nimbus-jose-jwt}, already a dependency for NHCX — no new
 * library, and one JOSE implementation in the build rather than two.
 *
 * <p>The two-token split is the security design, not a convenience:
 *
 * <ul>
 *   <li><b>Identity token</b> ({@code scope=PORTAL_IDENTITY}) says only "the
 *       holder proved possession of the number behind this HMAC token". It
 *       carries no patient id and grants no clinical read.</li>
 *   <li><b>Access token</b> ({@code scope=PORTAL_PATIENT}) says "the holder is
 *       this patient, at this tenant, at this branch".</li>
 * </ul>
 *
 * <p>Selecting a profile therefore has to go back through the server, which
 * re-checks the chosen patient against the OTP-verified number. With one token
 * type, a client holding any candidate list could edit the patient id and read
 * a sibling's records — the server would have nothing to check it against.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalTokenService {

    public static final String SCOPE_IDENTITY = "PORTAL_IDENTITY";
    public static final String SCOPE_PATIENT = "PORTAL_PATIENT";

    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_CONTACT_TOKEN = "ctk";
    private static final String CLAIM_PATIENT = "pid";
    private static final String CLAIM_TENANT = "tid";
    private static final String CLAIM_BRANCH = "bid";
    private static final String CLAIM_CHAIN = "cid";

    private final PortalProperties properties;

    /** A verified portal token, already checked for signature, issuer and expiry. */
    public record PortalClaims(
        String scope,
        String contactNumberToken,
        UUID patientId,
        UUID tenantId,
        UUID branchId,
        UUID chainId,
        Instant expiresAt) {

        public boolean isIdentityScope() {
            return SCOPE_IDENTITY.equals(scope);
        }

        public boolean isPatientScope() {
            return SCOPE_PATIENT.equals(scope);
        }
    }

    public String issueIdentityToken(String contactNumberToken) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(properties.getJwtIssuer())
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(properties.getIdentityTokenTtl())))
            .claim(CLAIM_SCOPE, SCOPE_IDENTITY)
            .claim(CLAIM_CONTACT_TOKEN, contactNumberToken)
            .build();
        return sign(claims);
    }

    public String issueAccessToken(UUID patientId, UUID tenantId, UUID branchId, UUID chainId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(properties.getJwtIssuer())
            .subject(patientId.toString())
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(properties.getAccessTokenTtl())))
            .claim(CLAIM_SCOPE, SCOPE_PATIENT)
            .claim(CLAIM_PATIENT, patientId.toString())
            .claim(CLAIM_TENANT, tenantId.toString())
            .claim(CLAIM_BRANCH, branchId.toString())
            .claim(CLAIM_CHAIN, chainId.toString())
            .build();
        return sign(claims);
    }

    /**
     * Parses and validates a token.
     *
     * <p>Every failure — bad signature, wrong issuer, expired, malformed —
     * returns the same {@code UNAUTHORIZED}. The distinction goes to the log,
     * because telling a caller which check failed tells an attacker which part
     * of their forgery to fix.
     */
    public PortalClaims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            if (!jwt.verify(new MACVerifier(secretBytes()))) {
                throw unauthorized("signature");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            if (!properties.getJwtIssuer().equals(claims.getIssuer())) {
                throw unauthorized("issuer");
            }

            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.toInstant().isBefore(Instant.now())) {
                throw unauthorized("expired");
            }

            String scope = claims.getStringClaim(CLAIM_SCOPE);
            if (!SCOPE_IDENTITY.equals(scope) && !SCOPE_PATIENT.equals(scope)) {
                throw unauthorized("scope");
            }

            return new PortalClaims(
                scope,
                claims.getStringClaim(CLAIM_CONTACT_TOKEN),
                uuidClaim(claims, CLAIM_PATIENT),
                uuidClaim(claims, CLAIM_TENANT),
                uuidClaim(claims, CLAIM_BRANCH),
                uuidClaim(claims, CLAIM_CHAIN),
                expiry.toInstant());

        } catch (ParseException | JOSEException e) {
            throw unauthorized("malformed");
        }
    }

    private String sign(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secretBytes()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign portal token", e);
        }
    }

    private byte[] secretBytes() {
        return Base64.getDecoder().decode(properties.getJwtSecret());
    }

    private static UUID uuidClaim(JWTClaimsSet claims, String name) throws ParseException {
        String raw = claims.getStringClaim(name);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ParseException("Claim " + name + " is not a UUID", 0);
        }
    }

    private PortalException unauthorized(String reason) {
        log.debug("event=portal.token.rejected reason={}", reason);
        return new PortalException(PortalErrorCode.UNAUTHORIZED, "portal.token.rejected:" + reason);
    }
}
