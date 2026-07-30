package com.hms.testutil;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Generates a valid JWE payload for testing the NHCX callback endpoint.
 *
 * <p>Run with: {@code ./gradlew -q test --tests "com.hms.testutil.NhcxTestPayloadGenerator" 2>/dev/null}
 * or just run this main method from the IDE.
 *
 * <p>It will:
 * <ol>
 *   <li>Generate an RSA key pair for signing (hospital) and encryption (our key)</li>
 *   <li>Save the JWK files to {@code /tmp/nhcx-test-keys/}</li>
 *   <li>Build a FHIR ClaimResponse bundle (approved, ₹50,000)</li>
 *   <li>Sign (JWS RS256) then encrypt (JWE RSA-OAEP-256 / A256GCM)</li>
 *   <li>Print the curl command with the real JWE payload</li>
 * </ol>
 *
 * <p><b>To make the backend actually process it end-to-end</b>, you also need:
 * <ul>
 *   <li>The env vars pointing to the generated keys (printed below)</li>
 *   <li>A matching {@code nhcx_transactions} row in the DB</li>
 * </ul>
 */
public class NhcxTestPayloadGenerator {

    private static final String SENDER_CODE = "STAR_HEALTH_01";
    private static final String HOSPITAL_CODE = "HMS_HOSPITAL_01";

    public static void main(String[] args) throws Exception {

        // ── 1. Generate RSA key pairs ──────────────────────────────────────
        RSAKey signingKey = new RSAKeyGenerator(2048)
                .keyID("test-signing-key-1")
                .generate();

        RSAKey encryptionKey = new RSAKeyGenerator(2048)
                .keyID("test-encryption-key-1")
                .generate();

        // The "sender" (payer) signing key — we need its public key to verify
        // In real NHCX, the payer signs with their private key and we verify
        // with their public key. For testing, sender = signer.
        RSAKey senderSigningKey = new RSAKeyGenerator(2048)
                .keyID("test-sender-signing-key-1")
                .generate();

        // ── 2. Save keys to disk ───────────────────────────────────────────
        Path keyDir = Path.of(System.getProperty("user.home"), ".hms", "nhcx-test-keys");
        Files.createDirectories(keyDir);

        // Hospital's signing key (full key pair — private + public)
        Path signingKeyPath = keyDir.resolve("signing.jwk");
        Files.writeString(signingKeyPath, signingKey.toJSONString());

        // Our encryption key (full key pair — private + public, needed for decrypt)
        Path encryptionKeyPath = keyDir.resolve("encryption.jwk");
        Files.writeString(encryptionKeyPath, encryptionKey.toJSONString());

        // Sender/payer's public key — saved as <sender_code>.jwk in same dir
        // The codec resolves payer keys as: <encryption key dir>/<participant_code>.jwk
        Path senderPubKeyPath = keyDir.resolve(SENDER_CODE + ".jwk");
        Files.writeString(senderPubKeyPath, senderSigningKey.toPublicJWK().toJSONString());

        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  NHCX Test Payload Generator");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Keys saved to: " + keyDir);
        System.out.println("  • signing.jwk          (hospital's signing key)");
        System.out.println("  • encryption.jwk       (our decryption key)");
        System.out.println("  • " + SENDER_CODE + ".jwk  (payer's public key)");
        System.out.println();

        // ── 3. Build the FHIR ClaimResponse bundle ─────────────────────────
        String correlationId = UUID.randomUUID().toString();

        String fhirBundle = """
                {
                  "resourceType": "Bundle",
                  "type": "collection",
                  "entry": [
                    {
                      "resource": {
                        "resourceType": "ClaimResponse",
                        "status": "active",
                        "use": "claim",
                        "outcome": "complete",
                        "disposition": "Claim settled as per policy terms",
                        "total": [
                          {
                            "category": {
                              "coding": [
                                {
                                  "system": "http://terminology.hl7.org/CodeSystem/adjudication",
                                  "code": "benefit"
                                }
                              ]
                            },
                            "amount": {
                              "value": 50000.00,
                              "currency": "INR"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        // ── 4. Sign (JWS) then encrypt (JWE) ──────────────────────────────
        // Step A: The SENDER (payer) signs the response with their private key
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(senderSigningKey.getKeyID())
                .type(JOSEObjectType.JOSE)
                .build();
        JWSObject jws = new JWSObject(jwsHeader, new Payload(fhirBundle));
        jws.sign(new RSASSASigner(senderSigningKey));

        // Step B: Encrypt to OUR public key (so only we can decrypt)
        JWEHeader jweHeader = new JWEHeader.Builder(
                JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                .keyID(encryptionKey.getKeyID())
                .contentType("JWS")
                .customParam("x-hcx-sender_code", SENDER_CODE)
                .customParam("x-hcx-recipient_code", HOSPITAL_CODE)
                .build();
        JWEObject jwe = new JWEObject(jweHeader, new Payload(jws.serialize()));
        jwe.encrypt(new RSAEncrypter(encryptionKey.toRSAPublicKey()));

        String jwePayload = jwe.serialize();

        // ── 5. Print everything ────────────────────────────────────────────
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println("  STEP 1: Set these env vars before starting the backend");
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("export HMS_GOV_NHCX_BASE_URL=https://staging-hcx.swasth.app");
        System.out.println("export HMS_GOV_NHCX_PARTICIPANT_CODE=" + HOSPITAL_CODE);
        System.out.println("export HMS_GOV_NHCX_CLIENT_ID=test-client");
        System.out.println("export HMS_GOV_NHCX_CLIENT_SECRET=test-secret");
        System.out.println("export HMS_GOV_NHCX_SIGNING_KEY_REF=" + signingKeyPath);
        System.out.println("export HMS_GOV_NHCX_ENCRYPTION_KEY_REF=" + encryptionKeyPath);
        System.out.println("export HMS_GOV_NHCX_CALLBACK_URL=http://localhost:8080/api/nhcx/callback");
        System.out.println();

        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println("  STEP 2: Insert a matching transaction row in your DB");
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("-- Run this SQL (adjust tenant_id/branch_id to match your data):");
        System.out.println(String.format("""
                INSERT INTO nhcx_transactions (
                    id, correlation_id, exchange_type, payer_code,
                    state, submitted_at, expires_at,
                    tenant_id, branch_id, created_at, updated_at
                ) VALUES (
                    gen_random_uuid(),
                    '%s',
                    'CLAIM',
                    '%s',
                    'SUBMITTED',
                    NOW(),
                    NOW() + INTERVAL '2 hours',
                    (SELECT id FROM tenants LIMIT 1),
                    (SELECT id FROM branches LIMIT 1),
                    NOW(),
                    NOW()
                );
                """, correlationId, SENDER_CODE));

        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println("  STEP 3: curl command (copy-paste this)");
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println();
        System.out.println(String.format("""
                curl -i -X POST http://localhost:8080/api/nhcx/callback/claim/on_submit \\
                  -H "Content-Type: application/json" \\
                  -H "x-hcx-correlation_id: %s" \\
                  -H "x-hcx-sender_code: %s" \\
                  -d '{"payload": "%s"}'
                """, correlationId, SENDER_CODE, jwePayload));

        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println("  Expected: HTTP 202 with {\"success\":true,\"message\":\"Received\"}");
        System.out.println("  The transaction row should update to state=APPROVED,");
        System.out.println("  approved_amount=5000000 (₹50,000 × 100 minor units)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Correlation ID: " + correlationId);
        System.out.println();

        // Also print raw payload for Postman users
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println("  Raw JSON body (for Postman)");
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("{\"payload\": \"" + jwePayload + "\"}");
        System.out.println();
    }
}
