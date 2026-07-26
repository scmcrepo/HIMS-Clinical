package com.hms.infrastructure.nhcx;

import com.hms.infrastructure.gov.GovApiException;
import com.hms.infrastructure.gov.GovApiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Signs and encrypts NHCX payloads (JWS then JWE), and reverses it for callbacks.
 *
 * <p>Isolated deliberately. Key material is the most sensitive thing in this
 * subsystem, so it touches one class with one audit surface rather than being
 * spread across the client, the callback handler and a test helper.
 *
 * <p><b>This class is intentionally incomplete.</b> The JOSE operations need a
 * real library — Nimbus JOSE+JWT is the usual choice — and, more importantly,
 * they need the hospital's actual signing key and the payer's public
 * certificate, which arrive with NHCX onboarding. Rather than ship a stub that
 * returns the payload unchanged and looks like it works, both methods fail
 * loudly with a message naming exactly what is missing. A silent no-op here
 * would mean transmitting unsigned patient claims to a national exchange.
 *
 * <p>To complete:
 * <ol>
 *   <li>Add {@code com.nimbusds:nimbus-jose-jwt}.</li>
 *   <li>Load the signing key from {@code hms.gov.nhcx.signing-key-ref} via your
 *       secrets manager — never from application.yml.</li>
 *   <li>JWS-sign with RS256, then JWE-encrypt to the payer certificate
 *       (RSA-OAEP-256 / A256GCM), carrying the NHCX protected headers.</li>
 *   <li>Reverse both for {@link #decryptAndVerify}, and reject any payload whose
 *       signature does not verify — an unsigned callback is not a callback.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NhcxPayloadCodec {

    private final GovApiProperties properties;

    public String signAndEncrypt(String json, String recipientParticipantCode) {
        assertKeysPresent();
        throw new GovApiException("NHCX_CODEC_NOT_IMPLEMENTED",
            "JWS/JWE assembly is not implemented. Add nimbus-jose-jwt and wire the signing key "
            + "from hms.gov.nhcx.signing-key-ref plus the payer certificate for "
            + recipientParticipantCode + ". See the class javadoc.", false);
    }

    public String decryptAndVerify(String jwe) {
        assertKeysPresent();
        throw new GovApiException("NHCX_CODEC_NOT_IMPLEMENTED",
            "JWE decryption and signature verification are not implemented. A callback whose "
            + "signature is not verified must never be trusted. See the class javadoc.", false);
    }

    private void assertKeysPresent() {
        GovApiProperties.Nhcx cfg = properties.getNhcx();
        if (isBlank(cfg.getSigningKeyRef()) || isBlank(cfg.getEncryptionKeyRef())) {
            throw new GovApiException("NHCX_KEYS_MISSING",
                "NHCX signing/encryption key references are not configured "
                + "(hms.gov.nhcx.signing-key-ref, encryption-key-ref).", false);
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
