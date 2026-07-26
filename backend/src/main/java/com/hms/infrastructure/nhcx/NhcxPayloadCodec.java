package com.hms.infrastructure.nhcx;

import com.hms.infrastructure.gov.GovApiException;
import com.hms.infrastructure.gov.GovApiProperties;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signs and encrypts NHCX payloads, and reverses it for callbacks.
 *
 * <p>Outbound is JWS-then-JWE: sign with the hospital's private key so the payer
 * can prove the claim came from this facility, then encrypt to the payer's public
 * key so nothing in between can read the patient's clinical detail. Inbound is
 * the reverse, and <b>the signature check is not optional</b> — a payload that
 * decrypts but does not verify is an unauthenticated claim response, and acting
 * on one would let anyone who can reach the callback URL approve or reject claims
 * on a payer's behalf.
 *
 * <p>Key material is loaded from the filesystem paths in
 * {@link GovApiProperties.Nhcx}. Those paths should be mounted secrets, not files
 * in the repository. Keys are cached after first load because JWK parsing is not
 * cheap and this sits on the claim submission path.
 *
 * <p><b>Verify the algorithm set against the current NHCX specification.</b>
 * RS256 / RSA-OAEP-256 / A256GCM below match the published NHCX profile at
 * authoring time, but the exchange has revised its protected-header requirements
 * before and a mismatch surfaces as an opaque gateway rejection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NhcxPayloadCodec {

    private static final JWSAlgorithm SIGNING_ALGORITHM = JWSAlgorithm.RS256;
    private static final JWEAlgorithm KEY_WRAP_ALGORITHM = JWEAlgorithm.RSA_OAEP_256;
    private static final EncryptionMethod CONTENT_ENCRYPTION = EncryptionMethod.A256GCM;

    private final GovApiProperties properties;

    /** Parsed keys, keyed by path. Cleared only by a restart, which is fine —
     *  rotating a key is already a deploy-level event. */
    private final Map<String, RSAKey> keyCache = new ConcurrentHashMap<>();

    /**
     * Sign then encrypt.
     *
     * @param recipientParticipantCode used to locate the payer's public key; each
     *                                 payer has its own certificate, so a single
     *                                 shared encryption key would be wrong
     */
    public String signAndEncrypt(String json, String recipientParticipantCode) {
        GovApiProperties.Nhcx cfg = properties.getNhcx();
        assertConfigured(cfg);

        try {
            RSAKey signingKey = loadKey(cfg.getSigningKeyRef());
            if (signingKey.toPrivateKey() == null) {
                throw new GovApiException("NHCX_SIGNING_KEY_PUBLIC_ONLY",
                    "The configured signing key has no private part; it cannot sign.", false);
            }

            JWSHeader jwsHeader = new JWSHeader.Builder(SIGNING_ALGORITHM)
                .keyID(signingKey.getKeyID())
                .type(JOSEObjectType.JOSE)
                .build();
            JWSObject jws = new JWSObject(jwsHeader, new Payload(json));
            jws.sign(new RSASSASigner(signingKey));

            RSAKey recipientKey = loadKey(recipientKeyPath(cfg, recipientParticipantCode));
            JWEHeader jweHeader = new JWEHeader.Builder(KEY_WRAP_ALGORITHM, CONTENT_ENCRYPTION)
                .keyID(recipientKey.getKeyID())
                .contentType("JWS")
                .customParam("x-hcx-sender_code", cfg.getParticipantCode())
                .customParam("x-hcx-recipient_code", recipientParticipantCode)
                .build();
            JWEObject jwe = new JWEObject(jweHeader, new Payload(jws.serialize()));
            jwe.encrypt(new RSAEncrypter(recipientKey.toRSAPublicKey()));

            return jwe.serialize();

        } catch (GovApiException e) {
            throw e;
        } catch (Exception e) {
            // The exception can carry fragments of the payload, which is patient
            // data. Only the type escapes.
            log.error("nhcx.codec.sign_encrypt.failed recipient[{}] type[{}]",
                      recipientParticipantCode, e.getClass().getSimpleName());
            throw new GovApiException("NHCX_PAYLOAD_FAILED",
                "Could not sign or encrypt the payload", false);
        }
    }

    /**
     * Decrypt then verify.
     *
     * <p>Order matters: decryption alone proves only that the payload was meant
     * for us, not who sent it. The signature check is what makes it a claim
     * response rather than an anonymous POST, so a verification failure throws
     * rather than returning the plaintext.
     */
    public String decryptAndVerify(String jweSerialised) {
        GovApiProperties.Nhcx cfg = properties.getNhcx();
        assertConfigured(cfg);

        try {
            RSAKey ourKey = loadKey(cfg.getEncryptionKeyRef());
            JWEObject jwe = JWEObject.parse(jweSerialised);
            jwe.decrypt(new RSADecrypter(ourKey.toPrivateKey()));

            JWSObject jws = JWSObject.parse(jwe.getPayload().toString());

            String senderCode = String.valueOf(
                jwe.getHeader().getCustomParam("x-hcx-sender_code"));
            RSAKey senderKey = loadKey(recipientKeyPath(cfg, senderCode));

            if (!jws.verify(new RSASSAVerifier(senderKey.toRSAPublicKey()))) {
                log.error("nhcx.callback.signature_invalid sender[{}]", senderCode);
                throw new GovApiException("NHCX_SIGNATURE_INVALID",
                    "Callback signature did not verify; the payload is not trusted", false);
            }

            return jws.getPayload().toString();

        } catch (GovApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("nhcx.codec.decrypt_verify.failed type[{}]", e.getClass().getSimpleName());
            throw new GovApiException("NHCX_PAYLOAD_UNREADABLE",
                "Could not decrypt or verify the callback payload", false);
        }
    }

    /**
     * Per-payer public key path.
     *
     * <p>Convention: {@code <encryptionKeyRef directory>/<participant-code>.jwk}.
     * Each payer has its own certificate, so encrypting every payer's traffic to
     * one shared key would both fail and be wrong.
     */
    private String recipientKeyPath(GovApiProperties.Nhcx cfg, String participantCode) {
        Path base = Path.of(cfg.getEncryptionKeyRef()).getParent();
        String safe = participantCode == null ? "unknown"
            : participantCode.replaceAll("[^A-Za-z0-9._-]", "_");
        return base == null ? safe + ".jwk" : base.resolve(safe + ".jwk").toString();
    }

    private RSAKey loadKey(String ref) {
        return keyCache.computeIfAbsent(ref, path -> {
            try {
                String jwk = Files.readString(Path.of(path), StandardCharsets.UTF_8);
                return RSAKey.parse(jwk);
            } catch (Exception e) {
                log.error("nhcx.key.load.failed ref[{}] type[{}]", path,
                          e.getClass().getSimpleName());
                throw new GovApiException("NHCX_KEY_UNREADABLE",
                    "Could not load the NHCX key at " + path
                    + ". It must be a JWK-format RSA key mounted as a secret.", false);
            }
        });
    }

    private void assertConfigured(GovApiProperties.Nhcx cfg) {
        if (isBlank(cfg.getSigningKeyRef()) || isBlank(cfg.getEncryptionKeyRef())) {
            throw new GovApiException("NHCX_KEYS_MISSING",
                "NHCX key references are not configured "
                + "(hms.gov.nhcx.signing-key-ref, encryption-key-ref).", false);
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
