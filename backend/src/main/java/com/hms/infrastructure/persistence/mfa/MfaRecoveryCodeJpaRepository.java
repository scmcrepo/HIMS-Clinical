package com.hms.infrastructure.persistence.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MfaRecoveryCodeJpaRepository extends JpaRepository<MfaRecoveryCodeEntity, UUID> {

    List<MfaRecoveryCodeEntity> findByCredentialIdAndUsedAtIsNull(UUID credentialId);

    long countByCredentialIdAndUsedAtIsNull(UUID credentialId);

    @Modifying
    @Query("DELETE FROM MfaRecoveryCodeEntity r WHERE r.credentialId = :credentialId")
    void deleteAllForCredential(@Param("credentialId") UUID credentialId);
}
